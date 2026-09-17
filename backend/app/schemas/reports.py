from datetime import date, datetime
from typing import Literal, Self

from pydantic import Field, model_validator

from app.domain.metrics import MetricKind, MetricUnit
from app.schemas.common import ApiModel

Dimension = Literal["stat_date", "stat_hour", "media", "product", "agency", "account", "operator", "campaign"]
MetricKey = Literal[
    "cost", "revenue", "impressions", "clicks", "launches", "callbacks", "conversions",
    "roi", "ctr", "cpm", "cpc", "click_arpu", "launch_rate", "cvr",
    "conversion_cost", "callback_cost", "launch_callback_rate", "revenue_gap",
]
ExportView = Literal["aggregate", "daily", "raw"]
CellValue = str | int | float | None
MAX_RANGE_DAYS = 93


class ReportFilters(ApiModel):
    media: list[str] = Field(default_factory=list, description="媒体；空列表表示不限")
    products: list[str] = Field(default_factory=list)
    agencies: list[str] = Field(default_factory=list)
    accounts: list[str] = Field(default_factory=list)
    operators: list[str] = Field(default_factory=list)


class DateRangeQuery(ApiModel):
    date_from: date
    date_to: date
    filters: ReportFilters = Field(default_factory=ReportFilters)

    @model_validator(mode="after")
    def check_range(self) -> Self:
        if self.date_to < self.date_from:
            raise ValueError("date_to 不能早于 date_from")
        if (self.date_to - self.date_from).days >= MAX_RANGE_DAYS:
            raise ValueError(f"单次查询跨度不超过 {MAX_RANGE_DAYS} 天")
        return self


class SortSpec(ApiModel):
    field: str = Field(description="列 key，维度或指标")
    direction: Literal["asc", "desc"] = "desc"


class ReportQuery(DateRangeQuery):
    keyword: str | None = Field(default=None, max_length=100, description="在维度值中搜索")
    group_by: list[Dimension] = Field(default_factory=lambda: ["product"], min_length=1, max_length=4)
    metrics: list[MetricKey] | None = Field(
        default=None, description="null 表示默认指标列；对外口径账号请求收益类指标返回 403"
    )
    sort: list[SortSpec] = Field(default_factory=list, max_length=3)
    page: int = Field(default=1, ge=1)
    page_size: int = Field(default=50, ge=1, le=500)


class RawDetailQuery(DateRangeQuery):
    keyword: str | None = Field(default=None, max_length=100)
    sort: list[SortSpec] = Field(default_factory=list, max_length=3)
    page: int = Field(default=1, ge=1)
    page_size: int = Field(default=100, ge=1, le=500)


class ReportColumn(ApiModel):
    key: str
    label: str
    kind: Literal["dimension"] | MetricKind
    unit: MetricUnit | None = None
    precision: int | None = None


class ReportResult(ApiModel):
    columns: list[ReportColumn]
    rows: list[dict[str, CellValue]]
    totals: dict[str, CellValue] = Field(description="当前筛选范围的合计，派生指标由合计基础指标重算")
    page: int
    page_size: int
    total_rows: int
    data_as_of: datetime | None


class TrendQuery(DateRangeQuery):
    granularity: Literal["day", "hour"] = "day"
    metrics: list[MetricKey] = Field(default_factory=lambda: ["cost", "roi"], min_length=1, max_length=4)
    split_by: Dimension | None = Field(default=None, description="按该维度拆成多条序列")


class TrendPoint(ApiModel):
    bucket: str
    values: dict[str, float | int | None]


class TrendSeries(ApiModel):
    key: str
    label: str
    points: list[TrendPoint]


class TrendResult(ApiModel):
    metrics: list[MetricKey]
    series: list[TrendSeries]
    data_as_of: datetime | None


class RoiAnomalyQuery(DateRangeQuery):
    group_by: list[Dimension] = Field(default_factory=lambda: ["account"], min_length=1, max_length=3)
    roi_below: float = Field(default=1.1, gt=0, le=10, description="ROI 低于该值视为异常")
    min_cost: float = Field(default=200, ge=0, description="消耗低于该值不参与判断，避免小额噪声")


class RoiAnomaly(ApiModel):
    dimensions: dict[str, str]
    cost: float
    revenue: float
    roi: float | None
    reason: str


class RoiAnomalyResult(ApiModel):
    rule: str
    items: list[RoiAnomaly]
    data_as_of: datetime | None


class Option(ApiModel):
    value: str
    label: str


class FilterOptions(ApiModel):
    media: list[Option]
    products: list[Option]
    agencies: list[Option]
    accounts: list[Option]
    operators: list[Option]
