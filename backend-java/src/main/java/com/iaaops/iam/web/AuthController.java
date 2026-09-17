package com.iaaops.iam.web;

import com.iaaops.iam.AuthService;
import com.iaaops.iam.PreferenceService;
import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.iam.domain.DataScope;
import com.iaaops.iam.domain.NavigationCatalog;
import com.iaaops.shared.metrics.Metric;
import com.iaaops.shared.metrics.MetricRegistry;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;
    private final PreferenceService preferenceService;

    AuthController(AuthService authService, PreferenceService preferenceService) {
        this.authService = authService;
        this.preferenceService = preferenceService;
    }

    @PostMapping("/auth/login")
    public AuthDtos.TokenPairResponse login(@Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest servletRequest) {
        return toResponse(authService.login(request.username(), request.password(), servletRequest.getRemoteAddr()));
    }

    @PostMapping("/auth/refresh")
    public AuthDtos.TokenPairResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return toResponse(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/password")
    public AuthDtos.TokenPairResponse changePassword(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody AuthDtos.PasswordChangeRequest request, HttpServletRequest servletRequest) {
        return toResponse(authService.changePassword(user, request.currentPassword(), request.newPassword(),
                servletRequest.getRemoteAddr()));
    }

    @GetMapping("/auth/me")
    public AuthDtos.PrincipalResponse me(@AuthenticationPrincipal CurrentUser user) {
        DataScope scope = user.dataScope();
        List<String> visible = MetricRegistry.visibleTo(user.seesRealMetrics()).stream().map(Metric::key).toList();
        return new AuthDtos.PrincipalResponse(
                user.id(), user.username(), user.displayName(), user.tenantId(), user.roles(),
                user.permissions().stream().sorted().toList(),
                new AuthDtos.DataScopeResponse(scope.agencies(), scope.products(), scope.operators()),
                user.mustChangePassword(), visible,
                NavigationCatalog.forPermissions(user.permissions()));
    }

    @GetMapping("/me/preferences")
    public AuthDtos.PreferencesResponse preferences(@AuthenticationPrincipal CurrentUser user) {
        return toResponse(preferenceService.get(user));
    }

    @PutMapping("/me/preferences")
    public AuthDtos.PreferencesResponse updatePreferences(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody AuthDtos.PreferencesRequest request) {
        PreferenceService.Preferences saved = preferenceService.update(user,
                new PreferenceService.Preferences(request.themeMode(), request.themePreset(), request.customPrimary(),
                        request.tableColumns() == null ? Map.of() : request.tableColumns(), request.revision()));
        return toResponse(saved);
    }

    private static AuthDtos.TokenPairResponse toResponse(AuthService.Tokens tokens) {
        return new AuthDtos.TokenPairResponse(tokens.accessToken(), tokens.refreshToken(), "bearer",
                tokens.expiresIn(), tokens.mustChangePassword());
    }

    private static AuthDtos.PreferencesResponse toResponse(PreferenceService.Preferences preferences) {
        return new AuthDtos.PreferencesResponse(preferences.themeMode(), preferences.themePreset(),
                preferences.customPrimary(), preferences.tableColumns(), preferences.revision());
    }
}
