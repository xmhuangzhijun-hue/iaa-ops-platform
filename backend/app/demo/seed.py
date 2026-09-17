"""生成固定种子的虚构演示数据。

    uv run python -m app.demo.seed --days 60

只写 tenant_demo 租户，并先清空该租户旧数据；production 或已有 Agent 数据时拒绝运行。
"""

import argparse
import math
import random
from collections.abc import Iterator
from dataclasses import dataclass
from datetime import date, timedelta
from decimal import ROUND_HALF_UP, Decimal
from typing import Any

from sqlalchemy import delete, insert, text
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.security import hash_password
from app.db import models as m

TENANT_ID = "tenant_demo"
DEFAULT_SEED = 20260916
# 公开演示口令：只对应虚构数据，部署公开演示时只开放只读账号。
DEMO_PASSWORD = "iaa-demo-2026"
BATCH_SIZE = 5000

MEDIA = ("vivo", "oppo", "huawei", "xiaomi")
PRODUCTS = ("晴空天气", "脑力答题王", "口袋清理", "极简记账", "萌宠消消乐", "每日菜谱", "快看小说", "步数宝")
AGENCIES = ("星河代理", "蓝鲸代理", "青橙代理")
OPERATORS = ("运营甲", "运营乙", "运营丙", "运营丁")
ACCOUNT_COUNT = 36
UNMAPPED_ACCOUNTS = 3
DEMO_USERS: tuple[tuple[str, str, tuple[str, ...], dict[str, list[str]]], ...] = (
    ("demo.admin", "演示超级管理员", ("super_admin",), {}),
    ("demo.company", "演示公司管理员", ("company_admin",), {}),
    ("demo.operator", "演示运营", ("operator",), {"operators": ["运营甲"]}),
    ("demo.agency", "演示代理管理员", ("agency_admin",), {"agencies": ["星河代理"]}),
    ("demo.readonly", "演示只读", ("readonly",), {}),
)


def _hour_weights() -> tuple[float, ...]:
    # 快应用投放常见的分时曲线：午间与晚间两个高峰，凌晨低谷。
    raw = [0.25 + math.exp(-((h - 12.5) / 2.5) ** 2) + 1.4 * math.exp(-((h - 20.5) / 2.2) ** 2) for h in range(24)]
    total = sum(raw)
    return tuple(value / total for value in raw)


HOUR_WEIGHTS = _hour_weights()


@dataclass(frozen=True)
class DemoAccount:
    media: str
    account: str
    campaign: str
    agency: str | None
    product: str | None
    operator: str | None
    daily_cost: float
    roi: float
    cpm: float
    ctr: float
    launch_rate: float
    cvr: float
    callback_rate: float
    phase: float


def build_accounts(rng: random.Random) -> list[DemoAccount]:
    accounts = []
    for index in range(ACCOUNT_COUNT):
        media = MEDIA[index % len(MEDIA)]
        mapped = index < ACCOUNT_COUNT - UNMAPPED_ACCOUNTS
        product = PRODUCTS[rng.randrange(len(PRODUCTS))]
        operator = OPERATORS[rng.randrange(len(OPERATORS))]
        accounts.append(DemoAccount(
            media=media, account=f"{media}-demo-{index + 1:03d}", campaign=f"plan-{index + 1:03d}",
            agency=AGENCIES[index % len(AGENCIES)] if mapped else None,
            product=product if mapped else None, operator=operator if mapped else None,
            daily_cost=rng.uniform(600, 9000), roi=rng.uniform(0.82, 1.28), cpm=rng.uniform(8, 22),
            ctr=rng.uniform(0.035, 0.08), launch_rate=rng.uniform(0.62, 0.85), cvr=rng.uniform(0.04, 0.12),
            callback_rate=rng.uniform(0.75, 0.95), phase=rng.uniform(0, math.tau),
        ))
    return accounts


def _money(value: float) -> Decimal:
    return Decimal(str(value)).quantize(Decimal("0.01"), ROUND_HALF_UP)


def generate_facts(rng: random.Random, accounts: list[DemoAccount], end: date, days: int) -> Iterator[dict[str, Any]]:
    for offset in range(days):
        day = end - timedelta(days=days - 1 - offset)
        weekend = 1.12 if day.weekday() >= 5 else 1.0
        for account in accounts:
            drift = 1 + 0.08 * math.sin(offset / 6 + account.phase)
            day_cost = account.daily_cost * weekend * rng.uniform(0.85, 1.15)
            for hour, weight in enumerate(HOUR_WEIGHTS):
                cost = day_cost * weight * rng.uniform(0.8, 1.2)
                impressions = int(cost / account.cpm * 1000)
                clicks = int(impressions * account.ctr * rng.uniform(0.9, 1.1))
                conversions = int(clicks * account.cvr * rng.uniform(0.85, 1.15))
                yield {
                    "tenant_id": TENANT_ID, "stat_date": day, "stat_hour": hour, "media": account.media,
                    "account": account.account, "campaign": account.campaign,
                    "cost": _money(cost), "revenue": _money(cost * account.roi * drift * rng.uniform(0.75, 1.25)),
                    "impressions": impressions, "clicks": clicks, "launches": int(clicks * account.launch_rate),
                    "conversions": conversions, "callbacks": int(conversions * account.callback_rate),
                }


def seed(session: Session, *, end: date, days: int, password: str = DEMO_PASSWORD,
         random_seed: int = DEFAULT_SEED) -> dict[str, int]:
    if settings.environment == "production":
        raise RuntimeError("拒绝在 production 环境写入演示数据")
    # Java 的 Agent 状态有独立生命周期，不能让旧种子的租户重置隐式清掉历史。
    # 先检查表是否存在，以兼容只有冻结 Alembic 迁移的 Python 对照库。
    for table in ("agent_campaigns", "agent_runs"):
        exists = session.execute(text("select to_regclass(:name)"), {"name": table}).scalar()
        if exists is not None and session.execute(text(f"select exists(select 1 from {table})")).scalar():
            raise RuntimeError(
                "本库已有 Agent 计划或运行，拒绝重置演示数据；请使用新的空库。"
                "只补充虚构计划请运行 python -m app.demo.agent_seed；已有数据不会被清空。"
            )
    rng = random.Random(random_seed)
    accounts = build_accounts(rng)

    # 用户相关表经外键级联删除。
    session.execute(delete(m.AdFact).where(m.AdFact.tenant_id == TENANT_ID))
    session.execute(delete(m.AccountMapping).where(m.AccountMapping.tenant_id == TENANT_ID))
    session.execute(delete(m.User).where(m.User.tenant_id == TENANT_ID))
    session.execute(delete(m.Tenant).where(m.Tenant.id == TENANT_ID))
    session.add(m.Tenant(id=TENANT_ID, name="演示租户"))
    session.flush()

    # 演示账号共用一个口令，只算一次哈希，节省内存与时间。
    password_hash = hash_password(password)
    for index, (username, display_name, _, scope) in enumerate(DEMO_USERS, 1):
        session.add(m.User(id=f"usr_demo_{index:02d}", tenant_id=TENANT_ID, username=username,
                           display_name=display_name, password_hash=password_hash, data_scope=scope))
    session.flush()
    session.add_all(
        m.UserRole(user_id=f"usr_demo_{index:02d}", role=role)
        for index, (_, _, roles, _) in enumerate(DEMO_USERS, 1)
        for role in roles
    )

    mapped = [account for account in accounts if account.product is not None]
    session.execute(insert(m.AccountMapping), [
        {"tenant_id": TENANT_ID, "media": a.media, "account": a.account,
         "agency": a.agency, "product": a.product, "operator": a.operator}
        for a in mapped
    ])

    facts = 0
    batch: list[dict[str, Any]] = []
    for row in generate_facts(rng, accounts, end, days):
        batch.append(row)
        if len(batch) == BATCH_SIZE:
            session.execute(insert(m.AdFact), batch)
            facts += len(batch)
            batch = []
    if batch:
        session.execute(insert(m.AdFact), batch)
        facts += len(batch)
    session.commit()
    return {"users": len(DEMO_USERS), "mappings": len(mapped), "facts": facts}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--end", type=date.fromisoformat, default=date.today() - timedelta(days=1))
    parser.add_argument("--days", type=int, default=60)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    args = parser.parse_args()

    from app.db.session import SessionLocal

    with SessionLocal() as session:
        counts = seed(session, end=args.end, days=args.days, random_seed=args.seed)
    print(f"已写入演示数据 {counts}；演示账号口令 {DEMO_PASSWORD}（仅对应虚构数据）")


if __name__ == "__main__":
    main()
