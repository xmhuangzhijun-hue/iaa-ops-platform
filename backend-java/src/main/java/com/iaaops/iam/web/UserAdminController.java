package com.iaaops.iam.web;

import com.iaaops.iam.UserAdminService;
import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.iam.domain.DataScope;
import com.iaaops.iam.domain.Permissions;
import com.iaaops.shared.error.ApiException;
import com.iaaops.shared.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {

    private final UserAdminService users;

    UserAdminController(UserAdminService users) {
        this.users = users;
    }

    @GetMapping
    public UserPage list(@AuthenticationPrincipal CurrentUser actor,
            @RequestParam(required = false) @Size(max = 64) String keyword,
            @RequestParam(required = false) @Pattern(regexp = "active|disabled") String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "page_size", defaultValue = "50") @Min(1) @Max(200) int pageSize) {
        require(actor);
        UserAdminService.Page result = users.list(actor, keyword, status, page, pageSize);
        return new UserPage(result.items().stream().map(UserAdminController::toSummary).toList(), result.total());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedResponse create(@AuthenticationPrincipal CurrentUser actor,
            @Valid @RequestBody CreateRequest request) {
        require(actor);
        UserAdminService.Created created = users.create(actor, request.username(), request.displayName(),
                request.roles(), scope(request.dataScope()));
        // 一次性口令只在这里出现一次；不写日志、不落库明文
        return new CreatedResponse(toSummary(created.user()), created.oneTimePassword(), true);
    }

    @PutMapping("/{userId}/roles")
    public Summary updateRoles(@AuthenticationPrincipal CurrentUser actor,
            @PathVariable @Size(max = 64) String userId,
            @Valid @RequestBody RolesUpdateRequest request) {
        require(actor);
        return toSummary(users.updateRoles(actor, userId, request.roles(), scope(request.dataScope()),
                request.revision()));
    }

    private static void require(CurrentUser user) {
        boolean allowed = Permissions.has(user.permissions(), Permissions.USERS_TEAM_MANAGE)
                || Permissions.has(user.permissions(), Permissions.USERS_AGENCY_MANAGE);
        if (!allowed) {
            throw new ApiException(ErrorCode.FORBIDDEN, "没有该操作的权限");
        }
    }

    private static DataScope scope(ScopeRequest request) {
        return request == null
                ? DataScope.UNLIMITED
                : new DataScope(request.agencies(), request.products(), request.operators());
    }

    private static Summary toSummary(UserAdminService.Summary summary) {
        DataScope scope = summary.dataScope();
        return new Summary(summary.id(), summary.username(), summary.displayName(), summary.roles(),
                summary.status(), new ScopeRequest(scope.agencies(), scope.products(), scope.operators()),
                summary.revision());
    }

    public record ScopeRequest(List<String> agencies, List<String> products, List<String> operators) {
    }

    public record Summary(String id, String username, String displayName, List<String> roles, String status,
            ScopeRequest dataScope, int revision) {
    }

    public record UserPage(List<Summary> items, long total) {
    }

    public record CreateRequest(
            @Pattern(regexp = "^[a-z0-9_.-]{3,32}$") String username,
            @NotNull @Size(min = 1, max = 32) String displayName,
            @NotEmpty List<@Pattern(regexp = "super_admin|company_admin|operator|agency_admin|customer|readonly")
                    String> roles,
            @Valid ScopeRequest dataScope) {
    }

    public record RolesUpdateRequest(
            @NotEmpty List<@Pattern(regexp = "super_admin|company_admin|operator|agency_admin|customer|readonly")
                    String> roles,
            @Valid ScopeRequest dataScope,
            @PositiveOrZero int revision) {
    }

    public record CreatedResponse(Summary user, String oneTimePassword, boolean mustChangePassword) {
    }
}
