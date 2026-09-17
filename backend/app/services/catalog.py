from app.domain.metrics import ALL_METRICS
from app.schemas.admin import MetricCatalog, MetricDefinition


def metric_catalog() -> MetricCatalog:
    return MetricCatalog(items=[
        MetricDefinition(
            key=spec.key, label=spec.label, kind=spec.kind, unit=spec.unit,
            formula=spec.formula, description=spec.description, precision=spec.precision,
        )
        for spec in ALL_METRICS
    ])
