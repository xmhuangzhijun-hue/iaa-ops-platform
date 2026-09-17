package com.iaaops.iam.security;

import com.iaaops.shared.config.AppProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String TYPE_CLAIM = "typ";
    private static final String TENANT_CLAIM = "tid";
    private static final String ACCESS = "access";
    private static final int MIN_KEY_BYTES = 32;

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AppProperties properties;

    JwtService(AppProperties properties) {
        this.properties = properties;
        SecretKey key = resolveKey(properties);
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static SecretKey resolveKey(AppProperties properties) {
        String configured = properties.jwtSigningKey();
        if (configured != null && !configured.isBlank()) {
            byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_KEY_BYTES) {
                throw new IllegalStateException("JWT_SIGNING_KEY 至少需要 32 字节");
            }
            return new SecretKeySpec(bytes, "HmacSHA256");
        }
        if (!properties.isLocal()) {
            throw new IllegalStateException("JWT_SIGNING_KEY 未配置：非本机环境必须由密钥管理注入");
        }
        // 本机开发每次启动随机生成，重启后需重新登录；从不写入文件。
        byte[] random = new byte[MIN_KEY_BYTES];
        new SecureRandom().nextBytes(random);
        log.warn("未配置 JWT_SIGNING_KEY，本次启动使用随机密钥（仅限本机开发）");
        return new SecretKeySpec(random, "HmacSHA256");
    }

    public AccessToken issue(String userId, String tenantId, Instant now) {
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId)
                .claim(TENANT_CLAIM, tenantId)
                .claim(TYPE_CLAIM, ACCESS)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new AccessToken(token, properties.accessTokenTtl().toSeconds());
    }

    /** 解析访问令牌，失败或类型不符返回空，由调用方转成 401。 */
    public java.util.Optional<String> resolveSubject(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            if (!ACCESS.equals(jwt.getClaimAsString(TYPE_CLAIM))) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.ofNullable(jwt.getSubject());
        } catch (JwtException exception) {
            return java.util.Optional.empty();
        }
    }

    public static String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record AccessToken(String value, long expiresInSeconds) {
    }
}
