from fastapi import APIRouter

from app.api import examples as ex
from app.api.contract import contract, ok
from app.core.config import settings
from app.schemas.admin import MetricCatalog
from app.schemas.common import Health
from app.services.catalog import metric_catalog

router = APIRouter()


@router.get(
    "/health", tags=["系统"], operation_id="getHealth", summary="健康检查",
    response_model=Health, response_description="服务可用",
    responses=ok(ex.HEALTH, "开发环境"),
    openapi_extra=contract(reqs=["IAA-REQ-005"], status="implemented", public=True),
)
def get_health() -> Health:
    return Health(status="ok", version=settings.version, environment=settings.environment)


@router.get(
    "/metrics", tags=["指标"], operation_id="listMetrics", summary="指标口径目录",
    description="返回全部基础指标与派生指标的名称、公式与精度；与后端计算共用同一份定义。",
    response_model=MetricCatalog, response_description="指标目录",
    responses=ok(ex.METRIC_CATALOG, "全部指标"),
    openapi_extra=contract(
        reqs=["IAA-REQ-003"], screens=["metric-catalog", "report-aggregate"],
        fields=["cost", "revenue", "roi", "cpc", "click_arpu"], status="implemented", public=True,
    ),
)
def list_metrics() -> MetricCatalog:
    return metric_catalog()
