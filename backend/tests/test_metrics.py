from decimal import Decimal

from app.domain.metrics import derive, summarize


def test_zero_denominator_is_none_not_zero():
    result = derive({"cost": 0, "revenue": 10, "clicks": 0})
    assert result["roi"] is None
    assert result["cpc"] is None
    assert result["click_arpu"] is None
    assert result["revenue_gap"] == Decimal(10)


def test_aggregate_ratio_is_recomputed_from_sums_not_averaged():
    rows = [
        {"product": "A", "cost": 100, "revenue": 200},
        {"product": "A", "cost": 900, "revenue": 900},
    ]
    (item,) = summarize(rows, ["product"])
    # 1100 / 1000；若平均两行比值会错得到 1.5
    assert item["roi"] == Decimal("1.1")


def test_groups_keep_first_seen_order_and_totals_add_up():
    rows = [{"product": "B", "cost": "1.10"}, {"product": "A", "cost": "2.20"}, {"product": "B", "cost": "0.10"}]
    grouped = summarize(rows, ["product"])
    assert [group["product"] for group in grouped] == ["B", "A"]
    assert grouped[0]["cost"] == Decimal("1.20")
    (total,) = summarize(rows)
    assert total["cost"] == Decimal("3.40")


def test_float_inputs_do_not_drift():
    (total,) = summarize([{"cost": 0.1}, {"cost": 0.2}])
    assert total["cost"] == Decimal("0.3")


def test_empty_rows_still_give_a_totals_row():
    (total,) = summarize([])
    assert total["cost"] == 0
    assert total["roi"] is None
