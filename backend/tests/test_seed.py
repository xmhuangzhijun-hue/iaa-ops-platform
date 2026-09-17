import random
from datetime import date

from app.demo.seed import ACCOUNT_COUNT, UNMAPPED_ACCOUNTS, build_accounts, generate_facts


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
