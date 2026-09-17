"""接口示例。

响应示例由模型实例或领域函数生成，保证与契约一致；所有名称与数值均为虚构演示数据。
"""

from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from typing import Any

from pydantic import BaseModel

from app.domain.access import navigation_for, permissions_for, visible_metric_keys
from app.domain.dimensions import DIMENSIONS
from app.domain.metrics import METRICS_BY_KEY, present, summarize
from app.schemas.admin import (
    AccountMapping, AccountMappingInput, AccountMappingPage, AccountMappingUpsert, AuditEvent,
    AuditEventPage, ImportTask, UpsertResult, UserCreate, UserCreated, UserPage, UserRolesUpdate,
    UserSummary,
)
from app.schemas.auth import DataScope, Preferences, PreferencesUpdate, Principal, TokenPair
from app.schemas.common import Health
from app.schemas.reports import (
    FilterOptions, Option, RawDetailQuery, ReportColumn, ReportFilters, ReportQuery, ReportResult,
    RoiAnomaly, RoiAnomalyQuery, RoiAnomalyResult, SortSpec, TrendPoint, TrendQuery, TrendResult,
    TrendSeries,
)
from app.services.catalog import metric_catalog

CST = timezone(timedelta(hours=8))
AS_OF = datetime(2026, 9, 15, 23, 50, tzinfo=CST)
DAY_FROM = date(2026, 9, 14)
DAY_TO = date(2026, 9, 15)
PRODUCTS = ["晴空天气", "脑力答题王"]

DEMO_ROWS: list[dict[str, Any]] = [
    {"stat_date": "2026-09-14", "stat_hour": 10, "media": "vivo", "product": "晴空天气", "agency": "星河代理",
     "account": "vivo-demo-003", "operator": "运营甲", "campaign": "qk-weather-01",
     "cost": "6120.40", "revenue": "6905.10", "impressions": 880000, "clicks": 46100,
     "launches": 34200, "callbacks": 2610, "conversions": 2880},
    {"stat_date": "2026-09-15", "stat_hour": 10, "media": "vivo", "product": "晴空天气", "agency": "星河代理",
     "account": "vivo-demo-003", "operator": "运营甲", "campaign": "qk-weather-01",
     "cost": "6720.10", "revenue": "7220.20", "impressions": 940000, "clicks": 50200,
     "launches": 37000, "callbacks": 2870, "conversions": 3140},
    {"stat_date": "2026-09-14", "stat_hour": 10, "media": "vivo", "product": "脑力答题王", "agency": "蓝鲸代理",
     "account": "vivo-demo-017", "operator": "运营乙", "campaign": "brain-quiz-02",
     "cost": "4180.00", "revenue": "3620.60", "impressions": 560000, "clicks": 30400,
     "launches": 22100, "callbacks": 1580, "conversions": 1790},
    {"stat_date": "2026-09-15", "stat_hour": 10, "media": "vivo", "product": "脑力答题王", "agency": "蓝鲸代理",
     "account": "vivo-demo-017", "operator": "运营乙", "campaign": "brain-quiz-02",
     "cost": "4240.00", "revenue": "4290.00", "impressions": 545000, "clicks": 29720,
     "launches": 21800, "callbacks": 1570, "conversions": 1770},
]


def dump(model: BaseModel) -> Any:
    return model.model_dump(mode="json")


def dump_request(model: BaseModel) -> Any:
    return model.model_dump(mode="json", exclude_defaults=True)


def _report(group_by: list[str], metrics: list[str], page_size: int) -> ReportResult:
    def cells(item: dict[str, Any], dimensions: list[str]) -> dict[str, Any]:
        row: dict[str, Any] = {dimension: item[dimension] for dimension in dimensions}
        for key in metrics:
            row[key] = present(key, item[key])
        return row

    grouped = summarize(DEMO_ROWS, group_by)
    (totals,) = summarize(DEMO_ROWS)
    columns = [ReportColumn(key=d, label=DIMENSIONS[d], kind="dimension") for d in group_by]
    columns += [
        ReportColumn(key=k, label=METRICS_BY_KEY[k].label, kind=METRICS_BY_KEY[k].kind,
                     unit=METRICS_BY_KEY[k].unit, precision=METRICS_BY_KEY[k].precision)
        for k in metrics
    ]
    return ReportResult(
        columns=columns, rows=[cells(item, group_by) for item in grouped], totals=cells(totals, []),
        page=1, page_size=page_size, total_rows=len(grouped), data_as_of=AS_OF,
    )


def _roi_anomalies(query: RoiAnomalyQuery) -> RoiAnomalyResult:
    threshold, min_cost = Decimal(str(query.roi_below)), Decimal(str(query.min_cost))
    items = []
    for item in summarize(DEMO_ROWS, query.group_by):
        roi = item["roi"]
        if item["cost"] >= min_cost and roi is not None and roi < threshold:
            items.append(RoiAnomaly(
                dimensions={d: str(item[d]) for d in query.group_by},
                cost=float(round(item["cost"], 2)), revenue=float(round(item["revenue"], 2)),
                roi=present("roi", roi), reason=f"ROI {roi:.1%} 低于 {threshold:.0%}",
            ))
    rule = f"ROI < {query.roi_below:.0%} 且消耗 ≥ {query.min_cost:g}"
    return RoiAnomalyResult(rule=rule, items=items, data_as_of=AS_OF)


HEALTH = dump(Health(status="ok", version="0.1.0", environment="development"))
METRIC_CATALOG = dump(metric_catalog())

LOGIN_REQUEST = {"username": "demo.operator", "password": "<密码>"}
REFRESH_REQUEST = {"refresh_token": "<refresh-token>"}
PASSWORD_CHANGE = {"current_password": "<当前口令>", "new_password": "<至少十位的新口令>"}
TOKEN_PAIR = dump(TokenPair(access_token="<access-token>", refresh_token="<refresh-token>",
                            expires_in=900, must_change_password=False))

_OPERATOR_PERMISSIONS = permissions_for(["operator"])
PRINCIPAL = dump(Principal.model_validate({
    "user_id": "usr_01", "username": "demo.operator", "display_name": "演示运营", "tenant_id": "tenant_demo",
    "roles": ["operator"], "permissions": sorted(_OPERATOR_PERMISSIONS),
    "data_scope": {"products": PRODUCTS}, "must_change_password": False,
    "visible_metrics": list(visible_metric_keys(_OPERATOR_PERMISSIONS)),
    "navigation": navigation_for(_OPERATOR_PERMISSIONS),
}))
_COLUMNS = {"report-aggregate": ["product", "cost", "revenue", "roi", "cpc", "click_arpu"]}
PREFERENCES_UPDATE = dump(PreferencesUpdate(theme_mode="dark", theme_preset="aurora-blue",
                                            table_columns=_COLUMNS, revision=3))
PREFERENCES = dump(Preferences(theme_mode="dark", theme_preset="aurora-blue", table_columns=_COLUMNS, revision=4))

_FILTERS = ReportFilters(media=["vivo"])
FILTER_OPTIONS = dump(FilterOptions(
    media=[Option(value="vivo", label="vivo"), Option(value="oppo", label="OPPO")],
    products=[Option(value=p, label=p) for p in PRODUCTS],
    agencies=[Option(value="星河代理", label="星河代理"), Option(value="蓝鲸代理", label="蓝鲸代理")],
    accounts=[Option(value="vivo-demo-003", label="vivo-demo-003"), Option(value="vivo-demo-017", label="vivo-demo-017")],
    operators=[Option(value="运营甲", label="运营甲"), Option(value="运营乙", label="运营乙")],
))

_AGGREGATE_METRICS = ["cost", "revenue", "roi", "cpc", "click_arpu"]
REPORT_QUERY = dump_request(ReportQuery(
    date_from=DAY_FROM, date_to=DAY_TO, filters=_FILTERS, group_by=["product"],
    metrics=_AGGREGATE_METRICS, sort=[SortSpec(field="cost", direction="desc")],
))
AGGREGATE_RESULT = dump(_report(["product"], _AGGREGATE_METRICS, 50))

DAILY_QUERY = dump_request(ReportQuery(
    date_from=DAY_FROM, date_to=DAY_TO, filters=_FILTERS, group_by=["stat_date", "product"],
    metrics=["cost", "revenue", "roi"],
))
DAILY_RESULT = dump(_report(["stat_date", "product"], ["cost", "revenue", "roi"], 50))

TREND_QUERY = dump_request(TrendQuery(date_from=DAY_FROM, date_to=DAY_TO, filters=_FILTERS, metrics=["cost", "roi"]))
TREND_RESULT = dump(TrendResult(
    metrics=["cost", "roi"],
    series=[TrendSeries(key="all", label="全部", points=[
        TrendPoint(bucket=day["stat_date"], values={"cost": present("cost", day["cost"]), "roi": present("roi", day["roi"])})
        for day in summarize(DEMO_ROWS, ["stat_date"])
    ])],
    data_as_of=AS_OF,
))

_ROI_QUERY = RoiAnomalyQuery(date_from=DAY_FROM, date_to=DAY_TO, filters=_FILTERS, group_by=["account", "product"])
ROI_ANOMALY_QUERY = dump_request(_ROI_QUERY)
ROI_ANOMALY_RESULT = dump(_roi_anomalies(_ROI_QUERY))

RAW_QUERY = dump_request(RawDetailQuery(date_from=DAY_TO, date_to=DAY_TO, filters=_FILTERS, keyword="晴空"))
RAW_RESULT = dump(_report(
    ["stat_date", "stat_hour", "account", "product", "campaign"], ["cost", "revenue", "clicks"], 100,
))

EXPORT_CSV = "产品,消耗,预估收益,ROI\n晴空天气,12840.50,14125.30,1.100\n脑力答题王,8420.00,7910.60,0.939\n"

MAPPING_PAGE = dump(AccountMappingPage(items=[
    AccountMapping(account="vivo-demo-003", media="vivo", agency="星河代理", product="晴空天气", operator="运营甲", revision=5),
    AccountMapping(account="vivo-demo-017", media="vivo", agency="蓝鲸代理", product="脑力答题王", operator="运营乙", revision=2),
], total=2))
MAPPING_UPSERT = dump_request(AccountMappingUpsert(items=[
    AccountMappingInput(account="vivo-demo-017", media="vivo", agency="蓝鲸代理", product="脑力答题王",
                        operator="运营甲", revision=2),
    AccountMappingInput(account="vivo-demo-021", media="vivo", agency="蓝鲸代理", product="晴空天气", operator="运营乙"),
]))
UPSERT_RESULT = dump(UpsertResult(created=1, updated=1, unchanged=0))

IMPORT_FORM = {"media": "vivo", "file": "（二进制 .xlsx 文件）"}
IMPORT_TASK = dump(ImportTask(
    id="imp_20260916_001", media="vivo", file_name="vivo-明细-20260915.xlsx", status="succeeded",
    stat_dates=[DAY_TO], rows_total=2328, rows_replaced=2310, errors=[],
    created_at=datetime(2026, 9, 16, 9, 12, tzinfo=CST), finished_at=datetime(2026, 9, 16, 9, 12, 41, tzinfo=CST),
))

_USER = UserSummary(id="usr_01", username="demo.operator", display_name="演示运营", roles=["operator"],
                    status="active", data_scope=DataScope(products=PRODUCTS), revision=2)
USER_PAGE = dump(UserPage(items=[_USER], total=1))
USER_CREATE = dump_request(UserCreate(username="demo.agency", display_name="演示代理管理员", roles=["agency_admin"],
                                      data_scope=DataScope(agencies=["星河代理"])))
USER_CREATED = dump(UserCreated(
    user=UserSummary(id="usr_02", username="demo.agency", display_name="演示代理管理员", roles=["agency_admin"],
                     status="active", data_scope=DataScope(agencies=["星河代理"]), revision=1),
    one_time_password="<仅本次返回的临时口令>",
))
ROLES_UPDATE = dump(UserRolesUpdate(roles=["operator"], data_scope=DataScope(products=PRODUCTS), revision=2))
USER_SUMMARY = dump(_USER.model_copy(update={"revision": 3}))

AUDIT_PAGE = dump(AuditEventPage(items=[
    AuditEvent(id="aud_9001", occurred_at=datetime(2026, 9, 16, 9, 30, tzinfo=CST), actor="demo.admin",
               action="MAPPING_UPSERTED", target_type="account_mapping", target_id="vivo-demo-017",
               detail={"field": "operator", "before": "运营乙", "after": "运营甲"}),
], next_cursor=None))
