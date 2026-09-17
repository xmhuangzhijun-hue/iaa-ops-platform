package com.iaaops.iam.domain;

import java.util.Collection;
import java.util.Set;

public final class Permissions {

    public static final String ALL = "*";
    public static final String DASHBOARD_READ = "dashboard.read";
    /** 真实口径：可见收益类指标 */
    public static final String METRICS_REAL = "metrics.real";
    /** 对外口径：代理与客户，收益类指标不可见 */
    public static final String METRICS_EXTERNAL = "metrics.external";
    public static final String USERS_TEAM_MANAGE = "users.team.manage";
    public static final String USERS_AGENCY_MANAGE = "users.agency.manage";
    public static final String MAPPINGS_MANAGE = "mappings.manage";
    public static final String IMPORTS_MANAGE = "imports.manage";
    public static final String AUDIT_READ = "audit.read";
    public static final String AGENT_EXECUTE = "agent.execute";

    private Permissions() {
    }

    public static boolean has(Collection<String> permissions, String required) {
        return permissions.contains(ALL) || permissions.contains(required);
    }

    public static void validate(Set<String> permissions) {
        if (!permissions.contains(ALL)
                && permissions.contains(METRICS_REAL) && permissions.contains(METRICS_EXTERNAL)) {
            throw new IllegalArgumentException("真实口径与对外口径不能同时授予同一账号");
        }
    }
}
