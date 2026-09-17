from datetime import date
from decimal import Decimal

from sqlalchemy import and_, func, select

from app.db import models as m
from app.db.session import SessionLocal

RANGE = {"date_from": "2026-09-09", "date_to": "2026-09-15"}
REPORTS = "/api/v1/reports/"


def post(client, headers, path, **body):
    return client.post(REPORTS + path, headers=headers, json={**RANGE, **body})


def database_totals(date_from=date(2026, 9, 9), date_to=date(2026, 9, 15), agency=None):
    facts, mappings = m.AdFact, m.AccountMapping
    stmt = select(func.sum(facts.cost), func.sum(facts.revenue)).where(facts.stat_date.between(date_from, date_to))
    if agency:
        stmt = stmt.join(mappings, and_(mappings.tenant_id == facts.tenant_id, mappings.media == facts.media,
                                        mappings.account == facts.account)).where(mappings.agency == agency)
    with SessionLocal() as session:
        cost, revenue = session.execute(stmt).one()
    return cost, revenue


def test_aggregate_totals_match_database_and_ratios_are_recomputed(client, auth_headers):
    body = post(client, auth_headers("demo.admin"), "aggregate",
                group_by=["product"], metrics=["cost", "revenue", "roi"]).json()
    cost, revenue = database_totals()

    assert body["totals"]["cost"] == float(cost)
    assert body["totals"]["roi"] == float(round(revenue / cost, 3))
    assert sum(Decimal(str(row["cost"])) for row in body["rows"]) == cost
    costs = [row["cost"] for row in body["rows"]]
    assert costs == sorted(costs, reverse=True)
    assert None in [row["product"] for row in body["rows"]], "未映射账户应作为空分组出现"


def test_agency_sees_only_its_scope_and_no_revenue(client, auth_headers):
    headers = auth_headers("demo.agency")
    body = post(client, headers, "aggregate", group_by=["agency"]).json()

    assert [row["agency"] for row in body["rows"]] == ["星河代理"]
    assert body["totals"]["cost"] == float(database_totals(agency="星河代理")[0])
    assert not {"revenue", "roi", "click_arpu"} & set(body["totals"])

    forbidden = post(client, headers, "aggregate", group_by=["product"], metrics=["roi"])
    assert forbidden.status_code == 403
    assert post(client, headers, "trend").status_code == 403
    export_raw = client.post(REPORTS + "export", params={"view": "raw"}, headers=headers, json=RANGE)
    assert export_raw.status_code == 403


def test_operator_scope_excludes_other_operators_and_unmapped(client, auth_headers):
    body = post(client, auth_headers("demo.operator"), "aggregate", group_by=["operator"]).json()
    assert [row["operator"] for row in body["rows"]] == ["运营甲"]


def test_keyword_sort_and_pagination(client, auth_headers):
    headers = auth_headers("demo.admin")
    body = post(client, headers, "aggregate", group_by=["account"], metrics=["cost"], keyword="vivo",
                sort=[{"field": "account", "direction": "asc"}], page=2, page_size=3).json()
    accounts = [row["account"] for row in body["rows"]]

    assert body["total_rows"] == 9
    assert accounts == ["vivo-demo-013", "vivo-demo-017", "vivo-demo-021"]

    bad_sort = post(client, headers, "aggregate", sort=[{"field": "nope", "direction": "asc"}])
    assert bad_sort.status_code == 422


def test_daily_puts_date_first_and_newest_on_top(client, auth_headers):
    body = post(client, auth_headers("demo.admin"), "daily", group_by=["media"], metrics=["cost"]).json()
    assert [column["key"] for column in body["columns"]][:2] == ["stat_date", "media"]
    assert body["total_rows"] == 7 * 4
    assert body["rows"][0]["stat_date"] == "2026-09-15"


def test_trend_by_day_split_and_by_hour(client, auth_headers):
    headers = auth_headers("demo.admin")
    daily_series = post(client, headers, "trend", metrics=["cost", "roi"], split_by="media").json()["series"]
    assert {series["key"] for series in daily_series} == {"vivo", "oppo", "huawei", "xiaomi"}
    assert all(len(series["points"]) == 7 for series in daily_series)

    hourly = client.post(REPORTS + "trend", headers=headers,
                         json={"date_from": "2026-09-15", "date_to": "2026-09-15", "granularity": "hour"}).json()
    points = hourly["series"][0]["points"]
    assert len(points) == 24
    assert points[0]["bucket"] == "2026-09-15 00:00"

    too_long = client.post(REPORTS + "trend", headers=headers,
                           json={"date_from": "2026-09-02", "date_to": "2026-09-15", "granularity": "hour"})
    assert too_long.status_code == 422


def test_roi_anomalies_agree_with_aggregate(client, auth_headers):
    headers = auth_headers("demo.admin")
    body = post(client, headers, "roi-anomalies", group_by=["account"], roi_below=1.0, min_cost=200).json()
    rows = post(client, headers, "aggregate", group_by=["account"], metrics=["cost", "revenue"],
                page_size=500).json()["rows"]
    expected = {row["account"] for row in rows if row["cost"] >= 200 and row["revenue"] < row["cost"]}

    assert body["rule"] == "ROI < 100% 且消耗 ≥ 200"
    assert {item["dimensions"]["account"] for item in body["items"]} == expected
    costs = [item["cost"] for item in body["items"]]
    assert costs == sorted(costs, reverse=True)


def test_raw_detail_paginates_with_database_totals(client, auth_headers):
    body = client.post(REPORTS + "raw", headers=auth_headers("demo.admin"),
                       json={"date_from": "2026-09-15", "date_to": "2026-09-15", "page_size": 20}).json()
    assert body["total_rows"] == 36 * 24
    assert len(body["rows"]) == 20
    assert body["rows"][0]["stat_hour"] == 23
    assert body["totals"]["cost"] == float(database_totals(date(2026, 9, 15), date(2026, 9, 15))[0])


def test_export_matches_the_on_screen_query(client, auth_headers):
    headers = auth_headers("demo.admin")
    query = {**RANGE, "group_by": ["product"], "metrics": ["cost", "roi"]}
    response = client.post(REPORTS + "export", params={"view": "aggregate"}, headers=headers, json=query)
    screen = client.post(REPORTS + "aggregate", headers=headers, json=query).json()

    assert response.headers["content-type"].startswith("text/csv")
    assert "attachment" in response.headers["content-disposition"]
    text = response.content.decode("utf-8")
    assert text.startswith("﻿产品,消耗,ROI\n")
    assert len(text.strip().splitlines()) == screen["total_rows"] + 1


def test_filter_options_respect_scope(client, auth_headers):
    admin = client.get("/api/v1/filter-options", params=RANGE, headers=auth_headers("demo.admin")).json()
    agency = client.get("/api/v1/filter-options", params=RANGE, headers=auth_headers("demo.agency")).json()

    assert len(admin["accounts"]) == 36
    assert {"value": "huawei", "label": "华为"} in admin["media"]
    assert [option["value"] for option in agency["agencies"]] == ["星河代理"]
    assert len(agency["accounts"]) == 11


def test_invalid_date_range_is_a_problem(client, auth_headers):
    response = client.post(REPORTS + "aggregate", headers=auth_headers("demo.admin"),
                           json={"date_from": "2026-09-10", "date_to": "2026-09-01"})
    assert response.status_code == 422
    assert response.headers["content-type"].startswith("application/problem+json")
    assert response.json()["code"] == "VALIDATION_FAILED"
