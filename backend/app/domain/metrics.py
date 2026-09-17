"""指标口径唯一定义处。

基础指标按行求和；派生指标只用汇总后的基础指标重算，从不平均明细比值。
分母为 0 时派生指标为 None（界面留空），不以 0 冒充。
"""

from __future__ import annotations

from collections.abc import Callable, Iterable, Mapping, Sequence
from dataclasses import dataclass
from decimal import Decimal
from typing import Any, Literal

MetricKind = Literal["base", "derived"]
MetricUnit = Literal["money", "count", "ratio", "per_unit"]
Number = Decimal | int | float | str
Compute = Callable[[Mapping[str, Decimal]], Decimal | None]


@dataclass(frozen=True)
class MetricSpec:
    key: str
    label: str
    kind: MetricKind
    unit: MetricUnit
    formula: str
    description: str
    precision: int


BASE_METRICS: tuple[MetricSpec, ...] = (
    MetricSpec("cost", "消耗", "base", "money", "sum(cost)", "媒体平台同步的投放消耗。", 2),
    MetricSpec("revenue", "预估收益", "base", "money", "sum(revenue)", "客户端变现的预估收益。", 2),
    MetricSpec("impressions", "曝光", "base", "count", "sum(impressions)", "媒体平台同步的曝光次数。", 0),
    MetricSpec("clicks", "点击", "base", "count", "sum(clicks)", "媒体平台同步的点击次数。", 0),
    MetricSpec("launches", "启动数", "base", "count", "sum(launches)", "快应用被拉起的次数。", 0),
    MetricSpec("callbacks", "回传数", "base", "count", "sum(callbacks)", "按回传规则回传给媒体的转化数。", 0),
    MetricSpec("conversions", "转化数", "base", "count", "sum(conversions)", "满足转化规则的转化数。", 0),
)


def _ratio(numerator: str, denominator: str, scale: int = 1) -> Compute:
    def compute(bases: Mapping[str, Decimal]) -> Decimal | None:
        if bases[denominator] == 0:
            return None
        return bases[numerator] * scale / bases[denominator]

    return compute


def _difference(minuend: str, subtrahend: str) -> Compute:
    return lambda bases: bases[minuend] - bases[subtrahend]


_DERIVED: tuple[tuple[MetricSpec, Compute], ...] = (
    (MetricSpec("roi", "ROI", "derived", "ratio", "revenue / cost",
                "预估收益与消耗之比；汇总行用总收益 / 总消耗重算。", 3), _ratio("revenue", "cost")),
    (MetricSpec("ctr", "点击率", "derived", "ratio", "clicks / impressions",
                "曝光到点击的比例。", 4), _ratio("clicks", "impressions")),
    (MetricSpec("cpm", "CPM", "derived", "per_unit", "cost / impressions × 1000",
                "千次曝光成本。", 2), _ratio("cost", "impressions", 1000)),
    (MetricSpec("cpc", "CPC", "derived", "per_unit", "cost / clicks",
                "每次点击成本。", 4), _ratio("cost", "clicks")),
    (MetricSpec("click_arpu", "点击 ARPU", "derived", "per_unit", "revenue / clicks",
                "每次点击带来的预估收益。", 4), _ratio("revenue", "clicks")),
    (MetricSpec("launch_rate", "启动率", "derived", "ratio", "launches / clicks",
                "点击到启动的跳转率。", 4), _ratio("launches", "clicks")),
    (MetricSpec("cvr", "转化率", "derived", "ratio", "conversions / clicks",
                "点击到转化的比例。", 4), _ratio("conversions", "clicks")),
    (MetricSpec("conversion_cost", "转化成本", "derived", "per_unit", "cost / conversions",
                "每次转化对应的消耗。", 4), _ratio("cost", "conversions")),
    (MetricSpec("callback_cost", "回传成本", "derived", "per_unit", "cost / callbacks",
                "每次回传对应的消耗。", 4), _ratio("cost", "callbacks")),
    (MetricSpec("launch_callback_rate", "启动→回传率", "derived", "ratio", "callbacks / launches",
                "启动到回传的比例。", 4), _ratio("callbacks", "launches")),
    (MetricSpec("revenue_gap", "回收差额", "derived", "money", "revenue - cost",
                "预估收益减消耗。", 2), _difference("revenue", "cost")),
)

DERIVED_METRICS: tuple[MetricSpec, ...] = tuple(spec for spec, _ in _DERIVED)
ALL_METRICS: tuple[MetricSpec, ...] = BASE_METRICS + DERIVED_METRICS
METRICS_BY_KEY: dict[str, MetricSpec] = {spec.key: spec for spec in ALL_METRICS}
BASE_KEYS: tuple[str, ...] = tuple(spec.key for spec in BASE_METRICS)
METRIC_KEYS: tuple[str, ...] = tuple(spec.key for spec in ALL_METRICS)


def to_decimal(value: Number | None) -> Decimal:
    if value is None or value == "":
        return Decimal(0)
    if isinstance(value, float):
        # 经 str 转换，避免把二进制浮点误差带进金额汇总。
        return Decimal(str(value))
    return Decimal(value)


def present(key: str, value: Decimal | int | None) -> int | float | None:
    """按指标精度输出给接口；None 保持为空。"""
    if value is None:
        return None
    precision = METRICS_BY_KEY[key].precision
    rounded = round(Decimal(value), precision)
    return int(rounded) if precision == 0 else float(rounded)


def derive(bases: Mapping[str, Number | None]) -> dict[str, Decimal | None]:
    normalized = {key: to_decimal(bases.get(key)) for key in BASE_KEYS}
    return {spec.key: compute(normalized) for spec, compute in _DERIVED}


def summarize(rows: Iterable[Mapping[str, Any]], group_by: Sequence[str] = ()) -> list[dict[str, Any]]:
    """按维度汇总基础指标并重算派生指标；不分组时返回一行合计。"""
    groups: dict[tuple[Any, ...], dict[str, Decimal]] = {}
    for row in rows:
        key = tuple(row.get(dimension) for dimension in group_by)
        sums = groups.setdefault(key, {base: Decimal(0) for base in BASE_KEYS})
        for base in BASE_KEYS:
            sums[base] += to_decimal(row.get(base))
    if not group_by and not groups:
        groups[()] = {base: Decimal(0) for base in BASE_KEYS}
    return [
        {**dict(zip(group_by, key, strict=True)), **sums, **derive(sums)}
        for key, sums in groups.items()
    ]
