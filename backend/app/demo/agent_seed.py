"""Insert fictional campaign controls after Java Flyway V3; never reset facts.

    python -m app.demo.agent_seed

Idempotent: existing budgets/bids and all Agent history stay unchanged.
"""
import random
from decimal import Decimal

from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import settings
from app.demo.seed import DEFAULT_SEED, TENANT_ID, build_accounts


def seed_agent(session: Session) -> int:
    if settings.environment not in {"development", "test"}:
        raise RuntimeError("Agent synthetic seed is limited to development/test")
    if not session.execute(text("select id from tenants where id = :tenant"),
                           {"tenant": TENANT_ID}).scalar():
        raise RuntimeError("Create the fictional tenant with app.demo.seed first")
    inserted = 0
    for index, account in enumerate(build_accounts(random.Random(DEFAULT_SEED)), 1):
        result = session.execute(text("""
            insert into agent_campaigns
                (id, tenant_id, media, account, campaign, name, bid, daily_budget, revision)
            values (:id, :tenant, :media, :account, :campaign, :name, :bid, :budget, 1)
            on conflict (tenant_id, media, account, campaign) do nothing
            """), {
                "id": f"demo_plan_{index:03d}", "tenant": TENANT_ID,
                "media": account.media, "account": account.account, "campaign": account.campaign,
                "name": f"{account.product or '未分配'} · {account.campaign}",
                "bid": Decimal("1.20") + Decimal(index % 5) / 10,
                "budget": Decimal(1000 + (index % 6) * 500),
            })
        inserted += result.rowcount
    session.commit()
    return inserted


def main() -> None:
    from app.db.session import SessionLocal
    with SessionLocal() as session:
        count = seed_agent(session)
    print(f"Added {count} fictional campaign controls; existing data unchanged")


if __name__ == "__main__":
    main()
