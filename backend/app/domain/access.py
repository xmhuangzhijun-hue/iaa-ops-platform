"""角色、权限与导航。

导航按权限裁剪只是体验；数据范围与接口权限一律在服务端强制。
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass

from app.domain.metrics import METRIC_KEYS

ALL = "*"
# 对外口径（代理、客户）看不到变现收益：收益及由收益派生的指标。
REVENUE_METRICS = frozenset({"revenue", "roi", "click_arpu", "revenue_gap"})

ROLE_PERMISSIONS: dict[str, frozenset[str]] = {
    "super_admin": frozenset({ALL}),
    "company_admin": frozenset({
        "dashboard.read", "metrics.real", "users.team.manage",
        "mappings.manage", "imports.manage", "audit.read", "agent.execute",
    }),
    "operator": frozenset({"dashboard.read", "metrics.real", "agent.execute"}),
    "agency_admin": frozenset({"dashboard.read", "metrics.external", "users.agency.manage"}),
    "customer": frozenset({"dashboard.read", "metrics.external"}),
    "readonly": frozenset({"dashboard.read"}),
}
ROLES: tuple[str, ...] = tuple(ROLE_PERMISSIONS)


@dataclass(frozen=True)
class NavPage:
    id: str
    title: str
    route: str
    requires_any: frozenset[str]


NAV_GROUPS: tuple[tuple[str, tuple[NavPage, ...]], ...] = (
    ("看盘分析", (
        NavPage("report-aggregate", "聚合", "/analysis/aggregate", frozenset({"dashboard.read"})),
        NavPage("report-daily", "分天明细", "/analysis/daily", frozenset({"dashboard.read"})),
        NavPage("report-trend", "趋势图", "/analysis/trend", frozenset({"metrics.real"})),
        NavPage("roi-anomalies", "ROI 异常清单", "/analysis/roi-anomalies", frozenset({"metrics.real"})),
        NavPage("raw-detail", "原始明细", "/analysis/raw", frozenset({"metrics.real"})),
    )),
    ("系统管理", (
        NavPage("user-manage", "用户与权限", "/system/users",
                frozenset({"users.team.manage", "users.agency.manage"})),
        NavPage("mapping-manage", "映射管理", "/system/mappings", frozenset({"mappings.manage"})),
        NavPage("data-import", "数据导入", "/system/imports", frozenset({"imports.manage"})),
        NavPage("metric-catalog", "指标与字段说明", "/system/metrics", frozenset({"dashboard.read"})),
        NavPage("audit-log", "审计日志", "/system/audit", frozenset({"audit.read"})),
    )),
)


def validate_permissions(permissions: Iterable[str]) -> None:
    values = set(permissions)
    if ALL not in values and {"metrics.real", "metrics.external"} <= values:
        raise ValueError("真实口径与对外口径不能同时授予同一账号")


def permissions_for(roles: Iterable[str]) -> frozenset[str]:
    granted: set[str] = set()
    for role in roles:
        if role not in ROLE_PERMISSIONS:
            raise ValueError(f"未知角色：{role}")
        granted |= ROLE_PERMISSIONS[role]
    validate_permissions(granted)
    return frozenset(granted)


def has_permission(permissions: Iterable[str], required: str) -> bool:
    values = set(permissions)
    return ALL in values or required in values


def visible_metric_keys(permissions: Iterable[str]) -> tuple[str, ...]:
    if has_permission(permissions, "metrics.real"):
        return METRIC_KEYS
    return tuple(key for key in METRIC_KEYS if key not in REVENUE_METRICS)


def navigation_for(permissions: Iterable[str]) -> list[dict[str, object]]:
    values = set(permissions)
    groups: list[dict[str, object]] = []
    for title, pages in NAV_GROUPS:
        visible = [
            {"id": page.id, "title": page.title, "route": page.route}
            for page in pages
            if ALL in values or values & page.requires_any
        ]
        if visible:
            groups.append({"title": title, "pages": visible})
    return groups
