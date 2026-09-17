package com.iaaops.iam.domain;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 角色到权限码的映射，与现有实现逐条一致。
 * 真实口径与对外口径互斥，不得同时授予同一账号（见 {@link Permissions#validate}）。
 */
public enum Role {

    SUPER_ADMIN("super_admin", Set.of(Permissions.ALL)),
    COMPANY_ADMIN("company_admin", Set.of(Permissions.DASHBOARD_READ, Permissions.METRICS_REAL,
            Permissions.USERS_TEAM_MANAGE, Permissions.MAPPINGS_MANAGE, Permissions.IMPORTS_MANAGE,
            Permissions.AUDIT_READ, Permissions.AGENT_EXECUTE)),
    OPERATOR("operator", Set.of(Permissions.DASHBOARD_READ, Permissions.METRICS_REAL, Permissions.AGENT_EXECUTE)),
    AGENCY_ADMIN("agency_admin", Set.of(Permissions.DASHBOARD_READ, Permissions.METRICS_EXTERNAL,
            Permissions.USERS_AGENCY_MANAGE)),
    CUSTOMER("customer", Set.of(Permissions.DASHBOARD_READ, Permissions.METRICS_EXTERNAL)),
    READONLY("readonly", Set.of(Permissions.DASHBOARD_READ));

    private final String code;
    private final Set<String> permissions;

    Role(String code, Set<String> permissions) {
        this.code = code;
        this.permissions = permissions;
    }

    public String code() {
        return code;
    }

    public Set<String> permissions() {
        return permissions;
    }

    public static Role fromCode(String code) {
        return Arrays.stream(values())
                .filter(role -> role.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知角色：" + code));
    }

    public static Set<String> permissionsOf(Iterable<String> roleCodes) {
        Set<String> granted = new LinkedHashSet<>();
        for (String code : roleCodes) {
            granted.addAll(fromCode(code).permissions());
        }
        Permissions.validate(granted);
        return granted;
    }
}
