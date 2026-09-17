package com.iaaops.iam.domain;

import java.util.List;
import java.util.Set;

/**
 * 请求上下文中的当前主体。角色与数据范围每次请求从库里读，停用或改权限立即生效。
 */
public record CurrentUser(
        String id,
        String tenantId,
        String username,
        String displayName,
        List<String> roles,
        Set<String> permissions,
        DataScope dataScope,
        boolean mustChangePassword) {

    public boolean can(String permission) {
        return Permissions.has(permissions, permission);
    }

    public boolean seesRealMetrics() {
        return can(Permissions.METRICS_REAL);
    }
}
