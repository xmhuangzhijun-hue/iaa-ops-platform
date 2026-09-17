package com.iaaops.iam.domain;

import java.util.List;
import java.util.Set;

/**
 * 契约里的 navigation 字段。
 *
 * 前端自 3A 起改用自己的菜单配置（`web/src/app/menu.ts`），这里保留是为了契约兼容，
 * 待契约下一次修订时移除。
 */
public final class NavigationCatalog {

    private record Page(String id, String title, String route, Set<String> requires) {
    }

    private record Group(String title, List<Page> pages) {
    }

    private static final List<Group> GROUPS = List.of(
            new Group("看盘分析", List.of(
                    new Page("report-aggregate", "聚合", "/analysis/aggregate", Set.of(Permissions.DASHBOARD_READ)),
                    new Page("report-daily", "分天明细", "/analysis/daily", Set.of(Permissions.DASHBOARD_READ)),
                    new Page("report-trend", "趋势图", "/analysis/trend", Set.of(Permissions.METRICS_REAL)),
                    new Page("roi-anomalies", "ROI 异常清单", "/analysis/roi-anomalies", Set.of(Permissions.METRICS_REAL)),
                    new Page("raw-detail", "原始明细", "/analysis/raw", Set.of(Permissions.METRICS_REAL)))),
            new Group("系统管理", List.of(
                    new Page("user-manage", "用户与权限", "/system/users",
                            Set.of(Permissions.USERS_TEAM_MANAGE, Permissions.USERS_AGENCY_MANAGE)),
                    new Page("mapping-manage", "映射管理", "/system/mappings", Set.of(Permissions.MAPPINGS_MANAGE)),
                    new Page("data-import", "数据导入", "/system/imports", Set.of(Permissions.IMPORTS_MANAGE)),
                    new Page("metric-catalog", "指标与字段说明", "/system/metrics", Set.of(Permissions.DASHBOARD_READ)),
                    new Page("audit-log", "审计日志", "/system/audit", Set.of(Permissions.AUDIT_READ)))));

    private NavigationCatalog() {
    }

    public static List<NavGroup> forPermissions(Set<String> permissions) {
        return GROUPS.stream()
                .map(group -> new NavGroup(group.title(), group.pages().stream()
                        .filter(page -> permissions.contains(Permissions.ALL)
                                || page.requires().stream().anyMatch(permissions::contains))
                        .map(page -> new NavPage(page.id(), page.title(), page.route()))
                        .toList()))
                .filter(group -> !group.pages().isEmpty())
                .toList();
    }

    public record NavPage(String id, String title, String route) {
    }

    public record NavGroup(String title, List<NavPage> pages) {
    }
}
