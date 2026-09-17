from datetime import date
from typing import Annotated
from urllib.parse import quote

from fastapi import APIRouter, Body, Depends, Query, Response

from app.api import examples as ex
from app.api.contract import contract, named_example, ok, problems
from app.api.deps import SessionDep, require
from app.schemas.reports import (
    ExportView, FilterOptions, RawDetailQuery, ReportQuery, ReportResult, RoiAnomalyQuery, RoiAnomalyResult,
    TrendQuery, TrendResult,
)
from app.services import reports as report_service
from app.services.auth import CurrentUser

router = APIRouter(tags=["看盘分析"])

Reader = Annotated[CurrentUser, Depends(require("dashboard.read"))]
Analyst = Annotated[CurrentUser, Depends(require("metrics.real"))]

DIMENSION_FIELDS = ["stat_date", "media", "product", "agency", "account", "operator"]
AGGREGATE_FIELDS = [*DIMENSION_FIELDS, "cost", "revenue", "clicks", "roi", "cpc", "click_arpu"]


class CsvResponse(Response):
    media_type = "text/csv"


@router.get(
    "/filter-options", operation_id="getFilterOptions", summary="筛选条可选值",
    description="返回日期范围内、当前账号数据范围可见的媒体、产品、代理、账户与运营。",
    response_model=FilterOptions, response_description="可选值",
    responses={**ok(ex.FILTER_OPTIONS, "运营账号可见范围"), **problems(401, 403)},
    openapi_extra=contract(reqs=["IAA-REQ-002"], screens=["app-shell"], fields=DIMENSION_FIELDS, status="implemented"),
)
def get_filter_options(
    date_from: Annotated[date, Query()], date_to: Annotated[date, Query()], session: SessionDep, user: Reader,
) -> FilterOptions:
    return report_service.filter_options(session, user, date_from, date_to)


@router.post(
    "/reports/aggregate", operation_id="queryAggregate", summary="聚合查询",
    description="按所选维度汇总；派生指标由汇总后的基础指标重算，合计行同理。结果受账号数据范围约束。",
    response_model=ReportResult, response_description="聚合结果",
    responses={**ok(ex.AGGREGATE_RESULT, "按产品聚合"), **problems(401, 403)},
    openapi_extra=contract(reqs=["IAA-REQ-002"], screens=["report-aggregate"], fields=AGGREGATE_FIELDS,
                           status="implemented"),
)
def query_aggregate(
    body: Annotated[ReportQuery, Body(openapi_examples=named_example(ex.REPORT_QUERY, "vivo 按产品聚合"))],
    session: SessionDep, user: Reader,
) -> ReportResult:
    return report_service.aggregate(session, user, body)


@router.post(
    "/reports/daily", operation_id="queryDaily", summary="分天明细",
    description="在聚合维度前固定加入日期维度，便于逐日对比；与聚合共用同一筛选与口径。",
    response_model=ReportResult, response_description="分天结果",
    responses={**ok(ex.DAILY_RESULT, "按日期与产品"), **problems(401, 403)},
    openapi_extra=contract(reqs=["IAA-REQ-002"], screens=["report-daily"], fields=AGGREGATE_FIELDS,
                           status="implemented"),
)
def query_daily(
    body: Annotated[ReportQuery, Body(openapi_examples=named_example(ex.DAILY_QUERY, "近两天按产品"))],
    session: SessionDep, user: Reader,
) -> ReportResult:
    return report_service.daily(session, user, body)


@router.post(
    "/reports/trend", operation_id="queryTrend", summary="趋势序列",
    description="按天或小时输出指标序列，可按一个维度拆成最多 8 条线，其余合并为「其他」。按小时时跨度不超过 7 天。",
    response_model=TrendResult, response_description="趋势序列",
    responses={**ok(ex.TREND_RESULT, "消耗与 ROI"), **problems(401, 403)},
    openapi_extra=contract(reqs=["IAA-REQ-002"], screens=["report-trend"],
                           fields=["stat_date", "stat_hour", "cost", "roi"], status="implemented"),
)
def query_trend(
    body: Annotated[TrendQuery, Body(openapi_examples=named_example(ex.TREND_QUERY, "按天看消耗与 ROI"))],
    session: SessionDep, user: Analyst,
) -> TrendResult:
    return report_service.trend(session, user, body)


@router.post(
    "/reports/roi-anomalies", operation_id="queryRoiAnomalies", summary="ROI 异常清单",
    description="列出消耗达到门槛且 ROI 低于阈值的对象；阈值与门槛随请求传入并回显在 rule 中。",
    response_model=RoiAnomalyResult, response_description="异常清单",
    responses={**ok(ex.ROI_ANOMALY_RESULT, "账户维度异常"), **problems(401, 403)},
    openapi_extra=contract(reqs=["IAA-REQ-002"], screens=["roi-anomalies"],
                           fields=["account", "product", "cost", "revenue", "roi"], status="implemented"),
)
def query_roi_anomalies(
    body: Annotated[RoiAnomalyQuery, Body(openapi_examples=named_example(ex.ROI_ANOMALY_QUERY, "ROI 低于 110%"))],
    session: SessionDep, user: Analyst,
) -> RoiAnomalyResult:
    return report_service.roi_anomalies(session, user, body)


@router.post(
    "/reports/raw", operation_id="queryRawDetail", summary="原始明细",
    description="不做汇总的入库明细，分页返回，用于核对导入数据。",
    response_model=ReportResult, response_description="明细分页",
    responses={**ok(ex.RAW_RESULT, "单日明细"), **problems(401, 403)},
    openapi_extra=contract(reqs=["IAA-REQ-002"], screens=["raw-detail"],
                           fields=[*DIMENSION_FIELDS, "stat_hour", "campaign", "cost", "revenue", "clicks"],
                           status="implemented"),
)
def query_raw_detail(
    body: Annotated[RawDetailQuery, Body(openapi_examples=named_example(ex.RAW_QUERY, "搜索产品关键词"))],
    session: SessionDep, user: Analyst,
) -> ReportResult:
    return report_service.raw_detail(session, user, body)


@router.post(
    "/reports/export", operation_id="exportReport", summary="导出 CSV",
    description="与页面使用同一查询与数据范围生成 CSV（UTF-8 带 BOM），导出结果与屏幕所见一致。原始明细导出需要真实口径权限。",
    response_class=CsvResponse, response_description="CSV 文件",
    responses={**ok(ex.EXPORT_CSV, "聚合导出", media_type="text/csv"), **problems(401, 403)},
    openapi_extra=contract(reqs=["IAA-REQ-002"], screens=["report-aggregate", "report-daily", "raw-detail"],
                           fields=AGGREGATE_FIELDS, status="implemented"),
)
def export_report(
    view: Annotated[ExportView, Query(description="导出哪个视图")],
    body: Annotated[ReportQuery, Body(openapi_examples=named_example(ex.REPORT_QUERY, "导出当前聚合"))],
    session: SessionDep, user: Reader,
) -> CsvResponse:
    content, filename = report_service.export_csv(session, user, view, body)
    return CsvResponse(content, headers={"Content-Disposition": f"attachment; filename*=UTF-8''{quote(filename)}"})
