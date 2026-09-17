"""看盘查询。

SQL 只负责过滤与基础指标求和；派生指标一律交给领域层用汇总值重算，与导出、合计共用一条路径。
数据范围在这里强制：账号的代理 / 产品 / 运营范围经账户映射过滤，未映射数据只对不限范围的账号可见。
"""

import csv
import io
from collections.abc import Sequence
from datetime import date
from decimal import Decimal
from typing import Any

from sqlalchemy import String, and_, cast, func, or_, select
from sqlalchemy.orm import Session
from sqlalchemy.sql.elements import ColumnElement

from app.core.errors import ApiError
from app.db import models as m
from app.domain.access import has_permission, visible_metric_keys
from app.domain.dimensions import DIMENSIONS, MEDIA_LABELS, UNMAPPED_LABEL
from app.domain.metrics import BASE_KEYS, METRICS_BY_KEY, present, summarize
from app.schemas.reports import (
    ExportView, FilterOptions, Option, RawDetailQuery, ReportColumn, ReportFilters, ReportQuery, ReportResult,
    RoiAnomaly, RoiAnomalyQuery, RoiAnomalyResult, SortSpec, TrendPoint, TrendQuery, TrendResult, TrendSeries,
)
from app.services.auth import CurrentUser

FACTS = m.AdFact.__table__
MAPPINGS = m.AccountMapping.__table__
SOURCE = FACTS.outerjoin(
    MAPPINGS,
    and_(MAPPINGS.c.tenant_id == FACTS.c.tenant_id, MAPPINGS.c.media == FACTS.c.media,
         MAPPINGS.c.account == FACTS.c.account),
)
DIMENSION_COLUMNS: dict[str, ColumnElement[Any]] = {
    "stat_date": FACTS.c.stat_date,
    "stat_hour": FACTS.c.stat_hour,
    "media": FACTS.c.media,
    "account": FACTS.c.account,
    "campaign": FACTS.c.campaign,
    "product": MAPPINGS.c.product,
    "agency": MAPPINGS.c.agency,
    "operator": MAPPINGS.c.operator,
}
RAW_DIMENSIONS = ["stat_date", "stat_hour", "media", "account", "campaign", "product", "agency", "operator"]
DEFAULT_METRICS = ("cost", "revenue", "roi", "clicks", "cpc", "click_arpu", "ctr", "cvr")
MAX_GROUPS = 20_000
MAX_EXPORT_ROWS = 100_000
MAX_HOURLY_DAYS = 7
TOP_SERIES = 8
OTHER_LABEL = "其他"


def _conditions(user: CurrentUser, date_from: date, date_to: date, filters: ReportFilters) -> list[ColumnElement[bool]]:
    conditions: list[ColumnElement[bool]] = [
        FACTS.c.tenant_id == user.tenant_id,
        FACTS.c.stat_date.between(date_from, date_to),
    ]
    requested = (
        (filters.media, "media"), (filters.accounts, "account"), (filters.products, "product"),
        (filters.agencies, "agency"), (filters.operators, "operator"),
    )
    for values, dimension in requested:
        if values:
            conditions.append(DIMENSION_COLUMNS[dimension].in_(values))
    for scope_key, dimension in (("agencies", "agency"), ("products", "product"), ("operators", "operator")):
        allowed = user.data_scope.get(scope_key)
        if allowed is not None:
            conditions.append(DIMENSION_COLUMNS[dimension].in_(allowed))
    return conditions


def _keyword(dimensions: Sequence[str], keyword: str) -> ColumnElement[bool]:
    escaped = keyword.replace("/", "//").replace("%", "/%").replace("_", "/_")
    pattern = f"%{escaped}%"
    return or_(*(cast(DIMENSION_COLUMNS[d], String).ilike(pattern, escape="/") for d in dimensions))


def resolve_metrics(user: CurrentUser, requested: Sequence[str] | None) -> list[str]:
    visible = visible_metric_keys(user.permissions)
    if requested is None:
        return [key for key in DEFAULT_METRICS if key in visible]
    hidden = [key for key in requested if key not in visible]
    if hidden:
        raise ApiError(403, "FORBIDDEN", "没有查看收益类指标的权限", f"无权指标：{', '.join(hidden)}")
    return list(dict.fromkeys(requested))


def _grouped_rows(session: Session, conditions: Sequence[ColumnElement[bool]], dimensions: Sequence[str]) -> list[dict[str, Any]]:
    columns = [DIMENSION_COLUMNS[d] for d in dimensions]
    stmt = (
        select(
            *(column.label(d) for column, d in zip(columns, dimensions, strict=True)),
            *(func.coalesce(func.sum(FACTS.c[key]), 0).label(key) for key in BASE_KEYS),
        )
        .select_from(SOURCE)
        .where(*conditions)
        .group_by(*columns)
        .limit(MAX_GROUPS + 1)
    )
    rows = [dict(row._mapping) for row in session.execute(stmt)]
    if len(rows) > MAX_GROUPS:
        raise ApiError(422, "RESULT_TOO_LARGE", "结果分组过多", f"超过 {MAX_GROUPS} 组，请缩小日期范围或减少分组维度")
    return rows


def _as_of(session: Session, conditions: Sequence[ColumnElement[bool]]) -> Any:
    return session.scalar(select(func.max(FACTS.c.loaded_at)).select_from(SOURCE).where(*conditions))


def _dimension_value(value: Any) -> Any:
    return value.isoformat() if isinstance(value, date) else value


def _row(item: dict[str, Any], dimensions: Sequence[str], metrics: Sequence[str]) -> dict[str, Any]:
    row = {d: _dimension_value(item[d]) for d in dimensions}
    row.update({key: present(key, item[key]) for key in metrics})
    return row


def _columns(dimensions: Sequence[str], metrics: Sequence[str]) -> list[ReportColumn]:
    columns = [ReportColumn(key=d, label=DIMENSIONS[d], kind="dimension") for d in dimensions]
    for key in metrics:
        spec = METRICS_BY_KEY[key]
        columns.append(ReportColumn(key=key, label=spec.label, kind=spec.kind, unit=spec.unit, precision=spec.precision))
    return columns


def _sort(items: list[dict[str, Any]], specs: Sequence[SortSpec], allowed: set[str]) -> list[dict[str, Any]]:
    for spec in specs:
        if spec.field not in allowed:
            raise ApiError(422, "VALIDATION_FAILED", "排序字段不在结果列中", spec.field)
    # 多键稳定排序：从最后一个键排起；空值不论升降序都排在最后。
    for spec in reversed(specs):
        present_items = [item for item in items if item[spec.field] is not None]
        missing_items = [item for item in items if item[spec.field] is None]
        present_items.sort(key=lambda item: item[spec.field], reverse=spec.direction == "desc")
        items = present_items + missing_items
    return items


def aggregate(
    session: Session,
    user: CurrentUser,
    query: ReportQuery,
    *,
    leading: Sequence[str] = (),
    default_sort: Sequence[SortSpec] = (SortSpec(field="cost"),),
    limit: int | None = None,
) -> ReportResult:
    dimensions = list(dict.fromkeys([*leading, *query.group_by]))
    metrics = resolve_metrics(user, query.metrics)
    base = _conditions(user, query.date_from, query.date_to, query.filters)
    conditions = [*base, _keyword(dimensions, query.keyword)] if query.keyword else base

    rows = _grouped_rows(session, conditions, dimensions)
    items = _sort(summarize(rows, dimensions), query.sort or default_sort, {*dimensions, *metrics})
    (totals,) = summarize(rows)

    page = 1 if limit else query.page
    page_size = limit or query.page_size
    start = (page - 1) * page_size
    return ReportResult(
        columns=_columns(dimensions, metrics),
        rows=[_row(item, dimensions, metrics) for item in items[start:start + page_size]],
        totals=_row(totals, [], metrics),
        page=page, page_size=page_size, total_rows=len(items), data_as_of=_as_of(session, base),
    )


def daily(session: Session, user: CurrentUser, query: ReportQuery, *, limit: int | None = None) -> ReportResult:
    return aggregate(
        session, user, query, leading=("stat_date",),
        default_sort=(SortSpec(field="stat_date"), SortSpec(field="cost")), limit=limit,
    )


def _label(value: Any) -> str:
    if value is None:
        return UNMAPPED_LABEL
    return value.isoformat() if isinstance(value, date) else str(value)


def trend(session: Session, user: CurrentUser, query: TrendQuery) -> TrendResult:
    metrics = resolve_metrics(user, query.metrics)
    hourly = query.granularity == "hour"
    if hourly and (query.date_to - query.date_from).days >= MAX_HOURLY_DAYS:
        raise ApiError(422, "VALIDATION_FAILED", f"按小时查看的跨度不超过 {MAX_HOURLY_DAYS} 天")
    buckets = ["stat_date", "stat_hour"] if hourly else ["stat_date"]
    conditions = _conditions(user, query.date_from, query.date_to, query.filters)
    if hourly:
        conditions.append(FACTS.c.stat_hour.is_not(None))
    split = [query.split_by] if query.split_by else []
    rows = _grouped_rows(session, conditions, [*buckets, *split])

    if query.split_by:
        key = query.split_by
        ranking = sorted(summarize(rows, split), key=lambda item: item["cost"], reverse=True)
        top = [_label(item[key]) for item in ranking[:TOP_SERIES]]
        grouped: dict[str, list[dict[str, Any]]] = {label: [] for label in top}
        for row in rows:
            label = _label(row[key])
            grouped.setdefault(label if label in top else OTHER_LABEL, []).append(row)
    else:
        grouped = {"全部": rows}

    def bucket(item: dict[str, Any]) -> str:
        day = item["stat_date"].isoformat()
        return f"{day} {item['stat_hour']:02d}:00" if hourly else day

    series = [
        TrendSeries(key=label, label=label, points=[
            TrendPoint(bucket=bucket(item), values={k: present(k, item[k]) for k in metrics})
            for item in sorted(summarize(group, buckets), key=lambda i: (i["stat_date"], i.get("stat_hour") or 0))
        ])
        for label, group in grouped.items()
    ]
    return TrendResult(metrics=metrics, series=series, data_as_of=_as_of(session, conditions))


def anomaly_rule(roi_below: float, min_cost: float) -> str:
    return f"ROI < {roi_below:.0%} 且消耗 ≥ {min_cost:g}"


def roi_anomalies(session: Session, user: CurrentUser, query: RoiAnomalyQuery) -> RoiAnomalyResult:
    conditions = _conditions(user, query.date_from, query.date_to, query.filters)
    rows = _grouped_rows(session, conditions, query.group_by)
    threshold, min_cost = Decimal(str(query.roi_below)), Decimal(str(query.min_cost))
    matched = [
        item for item in summarize(rows, query.group_by)
        if item["cost"] >= min_cost and item["roi"] is not None and item["roi"] < threshold
    ]
    matched.sort(key=lambda item: item["cost"], reverse=True)
    items = [
        RoiAnomaly(
            dimensions={d: _label(item[d]) for d in query.group_by},
            cost=float(round(item["cost"], 2)), revenue=float(round(item["revenue"], 2)),
            roi=present("roi", item["roi"]), reason=f"ROI {item['roi']:.1%} 低于 {threshold:.0%}",
        )
        for item in matched
    ]
    return RoiAnomalyResult(rule=anomaly_rule(query.roi_below, query.min_cost), items=items,
                            data_as_of=_as_of(session, conditions))


def raw_detail(session: Session, user: CurrentUser, query: RawDetailQuery, *, limit: int | None = None) -> ReportResult:
    visible = visible_metric_keys(user.permissions)
    metrics = [key for key in BASE_KEYS if key in visible]
    base = _conditions(user, query.date_from, query.date_to, query.filters)
    conditions = [*base, _keyword(["account", "campaign", "product"], query.keyword)] if query.keyword else base

    total_rows = session.scalar(select(func.count()).select_from(SOURCE).where(*conditions)) or 0
    if limit is not None and total_rows > limit:
        raise ApiError(422, "RESULT_TOO_LARGE", "导出行数过多", f"超过 {limit} 行，请缩小范围")

    specs = query.sort or [SortSpec(field="stat_date"), SortSpec(field="stat_hour"), SortSpec(field="cost")]
    sortable = {**{d: DIMENSION_COLUMNS[d] for d in RAW_DIMENSIONS}, **{k: FACTS.c[k] for k in metrics}}
    order_by = []
    for spec in specs:
        if spec.field not in sortable:
            raise ApiError(422, "VALIDATION_FAILED", "排序字段不在结果列中", spec.field)
        column = sortable[spec.field]
        order_by.append((column.desc() if spec.direction == "desc" else column.asc()).nulls_last())

    page = 1 if limit else query.page
    page_size = limit or query.page_size
    stmt = (
        select(*(DIMENSION_COLUMNS[d].label(d) for d in RAW_DIMENSIONS), *(FACTS.c[k] for k in metrics))
        .select_from(SOURCE).where(*conditions)
        .order_by(*order_by, FACTS.c.id)
        .offset((page - 1) * page_size).limit(page_size)
    )
    rows = [_row(dict(row._mapping), RAW_DIMENSIONS, metrics) for row in session.execute(stmt)]
    sums = session.execute(
        select(*(func.coalesce(func.sum(FACTS.c[k]), 0).label(k) for k in metrics)).select_from(SOURCE).where(*conditions)
    ).one()._mapping
    return ReportResult(
        columns=_columns(RAW_DIMENSIONS, metrics), rows=rows,
        totals={key: present(key, sums[key]) for key in metrics},
        page=page, page_size=page_size, total_rows=total_rows, data_as_of=_as_of(session, base),
    )


def _csv_value(column: ReportColumn, value: Any) -> Any:
    if value is None:
        return ""
    if column.kind == "dimension" or column.precision is None:
        return value
    return f"{value:.{column.precision}f}"


def export_csv(session: Session, user: CurrentUser, view: ExportView, query: ReportQuery) -> tuple[str, str]:
    if view == "raw":
        if not has_permission(user.permissions, "metrics.real"):
            raise ApiError(403, "FORBIDDEN", "没有导出原始明细的权限")
        raw_query = RawDetailQuery(
            date_from=query.date_from, date_to=query.date_to, filters=query.filters,
            keyword=query.keyword, sort=query.sort,
        )
        result = raw_detail(session, user, raw_query, limit=MAX_EXPORT_ROWS)
    elif view == "daily":
        result = daily(session, user, query, limit=MAX_EXPORT_ROWS)
    else:
        result = aggregate(session, user, query, limit=MAX_EXPORT_ROWS)

    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow([column.label for column in result.columns])
    for row in result.rows:
        writer.writerow([_csv_value(column, row.get(column.key)) for column in result.columns])
    filename = f"iaa-{view}-{query.date_from.isoformat()}_{query.date_to.isoformat()}.csv"
    # 带 BOM，Excel 直接打开中文不乱码。
    return "﻿" + buffer.getvalue(), filename


def filter_options(session: Session, user: CurrentUser, date_from: date, date_to: date) -> FilterOptions:
    if date_to < date_from:
        raise ApiError(422, "VALIDATION_FAILED", "date_to 不能早于 date_from")
    conditions = _conditions(user, date_from, date_to, ReportFilters())

    def distinct(dimension: str) -> list[Option]:
        column = DIMENSION_COLUMNS[dimension]
        stmt = select(column).select_from(SOURCE).where(*conditions, column.is_not(None)).distinct().order_by(column)
        labels = MEDIA_LABELS if dimension == "media" else {}
        return [Option(value=value, label=labels.get(value, value)) for value in session.scalars(stmt)]

    return FilterOptions(
        media=distinct("media"), products=distinct("product"), agencies=distinct("agency"),
        accounts=distinct("account"), operators=distinct("operator"),
    )
