import random
from datetime import date
from pathlib import Path

import pytest
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.demo.seed import ACCOUNT_COUNT, UNMAPPED_ACCOUNTS, build_accounts, generate_facts, seed


def generate(seed: int) -> list[dict]:
    rng = random.Random(seed)
    accounts = build_accounts(rng)
    return list(generate_facts(rng, accounts, date(2026, 9, 15), 1))


def test_same_seed_gives_identical_data():
    assert generate(7) == generate(7)
    assert generate(7) != generate(8)


def test_shape_of_one_day():
    rows = generate(7)
    assert len(rows) == ACCOUNT_COUNT * 24
    assert all(row["cost"] >= 0 and row["clicks"] <= row["impressions"] for row in rows)
    accounts = build_accounts(random.Random(7))
    assert sum(account.product is None for account in accounts) == UNMAPPED_ACCOUNTS


@pytest.mark.parametrize("table", ["agent_campaigns", "agent_runs"])
def test_seed_refuses_existing_agent_state_before_deleting_any_data(database, table):
    from app.db.session import engine
    from tests.support import SEED_DAYS, SEED_END, TEST_PASSWORD

    migration = Path(__file__).resolve().parents[2] / "backend-java/src/main/resources/db/migration/V3__agent_workbench.sql"
    with engine.connect() as connection:
        transaction = connection.begin()
        try:
            # Reuse the real Java migration, in a rollback-only fixture transaction.
            connection.exec_driver_sql(migration.read_text(encoding="utf-8"))
            if table == "agent_campaigns":
                connection.execute(text("""
                    insert into agent_campaigns(id,tenant_id,media,account,campaign,name,bid,daily_budget)
                    values ('guard_plan','tenant_demo','vivo','guard-account','guard-plan','虚构保护测试',1,100)
                    """))
            else:
                connection.execute(text("""
                    insert into agent_runs(id,tenant_id,owner_id,request_id,request_hash,scope_fingerprint,prompt,status)
                    values ('guard_run','tenant_demo','usr_demo_01','guard-request',:hash,:hash,'虚构保护测试','succeeded')
                    """), {"hash": "a" * 64})
            tables = ("tenants", "users", "account_mappings", "ad_facts", "agent_campaigns", "agent_runs")
            before = {name: connection.execute(text(f"select count(*) from {name}")).scalar_one() for name in tables}
            with Session(bind=connection) as session:
                with pytest.raises(RuntimeError, match="app.demo.agent_seed"):
                    seed(session, end=SEED_END, days=SEED_DAYS, password=TEST_PASSWORD)
                after = {name: connection.execute(text(f"select count(*) from {name}")).scalar_one() for name in tables}
                assert after == before
        finally:
            transaction.rollback()
