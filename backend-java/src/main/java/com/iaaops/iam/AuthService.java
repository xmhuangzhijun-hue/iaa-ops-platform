package com.iaaops.iam;

import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.iam.domain.DataScope;
import com.iaaops.iam.domain.Role;
import com.iaaops.iam.persistence.RefreshTokenEntity;
import com.iaaops.iam.persistence.RefreshTokenRepository;
import com.iaaops.iam.persistence.UserEntity;
import com.iaaops.iam.persistence.UserRepository;
import com.iaaops.iam.persistence.UserRoleRepository;
import com.iaaops.iam.security.JwtService;
import com.iaaops.shared.config.AppProperties;
import com.iaaops.shared.error.ApiException;
import com.iaaops.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AuthService {

    /** 账号不存在时也做一次校验，避免响应时间暴露账号是否存在。 */
    private static final String TIMING_HASH =
            "$argon2id$v=19$m=65536,t=3,p=1$c29tZXNhbHRzb21lc2E$dnEQ0sTKJ1nvKPDvhCeLLbxRZ0pjkuVcLQJ4qJ0Wq0M";

    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;
    private final LoginProtection loginProtection;

    AuthService(UserRepository users, UserRoleRepository userRoles,
            RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder, JwtService jwtService,
            AppProperties properties, LoginProtection loginProtection) {
        this.users = users;
        this.userRoles = userRoles;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.loginProtection = loginProtection;
    }

    @Transactional
    public Tokens login(String username, String password, String address) {
        LoginProtection.Attempt attempt = loginProtection.begin(username, address);
        boolean transactionOwnsAttempt = false;
        try {
            Optional<UserEntity> found = users.findByUsername(username);
            boolean matched = passwordEncoder.matches(password,
                    found.map(UserEntity::getPasswordHash).orElse(TIMING_HASH));
            if (found.isEmpty() || !matched || !found.get().isActive()) {
                attempt.failed();
                throw LoginProtection.rejected();
            }
            Tokens tokens = issue(found.get(), OffsetDateTime.now(ZoneOffset.UTC));
            resetLoginProtectionAfterCommit(username, address, attempt);
            transactionOwnsAttempt = true;
            return tokens;
        } finally {
            if (!transactionOwnsAttempt) attempt.close();
        }
    }

    // 重放检测要把"吊销该账号全部刷新令牌"落库，因此这条路径上的 ApiException 不触发回滚：
    // 否则异常一抛，吊销随事务一起回滚，泄露的令牌仍然可用。
    @Transactional(noRollbackFor = ApiException.class)
    public Tokens refresh(String rawToken) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RefreshTokenEntity token = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("刷新令牌无效"));

        if (token.isRevoked()) {
            // 已轮换的令牌被再次使用，按泄露处理：吊销该账号全部刷新令牌。
            refreshTokens.revokeAllActive(token.getUserId(), now);
            throw ApiException.unauthorized("刷新令牌已失效，请重新登录");
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw ApiException.unauthorized("刷新令牌已过期，请重新登录");
        }
        UserEntity user = users.findById(token.getUserId()).filter(UserEntity::isActive)
                .orElseThrow(() -> ApiException.unauthorized("账号不存在或已停用"));
        token.revoke(now);
        return issue(user, now);
    }

    /** 吊销单个刷新令牌；未知或已吊销的静默忽略，不触发重放检测。 */
    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Transactional
    public Tokens changePassword(CurrentUser current, String currentPassword, String newPassword, String address) {
        UserEntity user = users.findById(current.id())
                .orElseThrow(() -> ApiException.unauthorized("账号不存在"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "当前口令错误");
        }
        if (currentPassword.equals(newPassword)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "新口令不能与当前口令相同");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.bumpRevision();
        refreshTokens.revokeAllActive(user.getId(), now);
        resetLoginProtectionAfterCommit(user.getUsername(), address, null);
        return issue(user, now);
    }

    private void resetLoginProtectionAfterCommit(String username, String address, LoginProtection.Attempt attempt) {
        // 只有改密/令牌事务真正落库才清零，提交失败不能意外解除锁定。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                loginProtection.reset(username, address);
            }

            @Override
            public void afterCompletion(int status) {
                if (attempt != null) attempt.close();
            }
        });
    }

    @Transactional(readOnly = true)
    public Optional<CurrentUser> loadCurrentUser(String userId) {
        return users.findById(userId).filter(UserEntity::isActive).map(user -> {
            List<String> roles = userRoles.findRoles(user.getId());
            Set<String> permissions = Role.permissionsOf(roles);
            return new CurrentUser(user.getId(), user.getTenantId(), user.getUsername(), user.getDisplayName(),
                    roles, permissions, DataScope.fromJson(user.getDataScope()), user.isMustChangePassword());
        });
    }

    private Tokens issue(UserEntity user, OffsetDateTime now) {
        JwtService.AccessToken access = jwtService.issue(user.getId(), user.getTenantId(), now.toInstant());
        String raw = JwtService.randomToken();
        refreshTokens.save(new RefreshTokenEntity(user.getId(), hash(raw),
                now.plusSeconds(properties.refreshTokenTtl().toSeconds())));
        return new Tokens(access.value(), raw, access.expiresInSeconds(), user.isMustChangePassword());
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Tokens(String accessToken, String refreshToken, long expiresIn, boolean mustChangePassword) {
    }

    /** 供测试构造固定时间点的令牌。 */
    Instant now() {
        return Instant.now();
    }
}
