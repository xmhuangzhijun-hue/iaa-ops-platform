package com.iaaops.iam.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iaaops.iam.domain.NavigationCatalog;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** 请求与响应模型，字段名经 SNAKE_CASE 策略序列化，与 contracts/openapi.json 一致。 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 128) String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record PasswordChangeRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {
    }

    public record TokenPairResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            boolean mustChangePassword) {
    }

    public record DataScopeResponse(List<String> agencies, List<String> products, List<String> operators) {
    }

    public record PrincipalResponse(
            String userId,
            String username,
            String displayName,
            String tenantId,
            List<String> roles,
            List<String> permissions,
            DataScopeResponse dataScope,
            boolean mustChangePassword,
            List<String> visibleMetrics,
            List<NavigationCatalog.NavGroup> navigation) {
    }

    public record PreferencesRequest(
            @NotNull @Pattern(regexp = "light|dark|system") String themeMode,
            @NotNull @Pattern(regexp = "aurora-blue|arc-purple|quantum-cyan|pulse-green|corona-gold|molten-orange"
                    + "|rose-wave|glacier-blue|nebula-purple|deep-space-gray|neon-cyan|custom") String themePreset,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String customPrimary,
            Map<String, List<String>> tableColumns,
            @PositiveOrZero int revision) {

        @JsonIgnore
        @AssertTrue(message = "theme_preset 为 custom 时必须提供 custom_primary")
        public boolean isCustomPrimaryPresentWhenNeeded() {
            return !"custom".equals(themePreset) || customPrimary != null;
        }
    }

    public record PreferencesResponse(
            String themeMode,
            String themePreset,
            String customPrimary,
            Map<String, List<String>> tableColumns,
            int revision) {
    }
}
