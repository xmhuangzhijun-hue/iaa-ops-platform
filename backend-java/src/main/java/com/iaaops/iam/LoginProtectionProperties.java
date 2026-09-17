package com.iaaops.iam;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.login-protection")
public record LoginProtectionProperties(int accountFailures, int ipFailures, Duration window,
        Duration lockDuration, int maxEntries) {
    public LoginProtectionProperties {
        if (accountFailures < 1 || ipFailures < 1 || maxEntries < 1
                || window == null || window.isNegative() || window.isZero()
                || lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("登录保护的阈值、容量与时长必须为正数");
        }
    }
}
