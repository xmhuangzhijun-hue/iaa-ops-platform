package com.iaaops.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param environment    运行环境标识，出现在健康检查响应中
 * @param version        对外展示的服务版本
 * @param jwtSigningKey  访问令牌签名密钥；非本机环境必须注入，缺失则拒绝启动
 * @param accessTokenTtl 访问令牌有效期
 * @param refreshTokenTtl 刷新令牌有效期
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String environment,
        String version,
        String jwtSigningKey,
        Duration accessTokenTtl,
        Duration refreshTokenTtl) {

    public boolean isLocal() {
        return "development".equals(environment) || "test".equals(environment);
    }
}
