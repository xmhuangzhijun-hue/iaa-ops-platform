package com.iaaops.iam;

import com.iaaops.governance.AuditLog;
import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.iam.domain.DataScope;
import com.iaaops.iam.domain.Permissions;
import com.iaaops.iam.domain.Role;
import com.iaaops.iam.persistence.UserEntity;
import com.iaaops.iam.persistence.UserRepository;
import com.iaaops.iam.persistence.UserRoleEntity;
import com.iaaops.iam.persistence.UserRoleRepository;
import com.iaaops.shared.Ids;
import com.iaaops.shared.error.ApiException;
import com.iaaops.shared.error.ErrorCode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号与权限管理。
 *
 * 两条不可越过的线：谁都不能授出自己没有的权限，也不能把数据范围放得比自己宽。
 * 否则一个代理管理员可以先给自己建一个公司管理员账号，权限体系就形同虚设。
 */
@Service
public class UserAdminService {

    private static final String PASSWORD_ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int PASSWORD_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwordEncoder;
    private final AuditLog audit;

    UserAdminService(UserRepository users, UserRoleRepository userRoles, PasswordEncoder passwordEncoder,
            AuditLog audit) {
        this.users = users;
        this.userRoles = userRoles;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page list(CurrentUser actor, String keyword, String status, int page, int pageSize) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<UserEntity> rows = users.page(actor.tenantId(), normalized, status,
                PageRequest.of(page - 1, pageSize));
        // 三个授权维度都必须满足；不能仅检查代理而遗漏产品/运营边界。
        List<UserEntity> visible = rows.stream().filter(user -> visibleTo(actor, user)).toList();
        long total = actor.dataScope().unlimited()
                ? users.countMatching(actor.tenantId(), normalized, status)
                : visible.size();
        return new Page(visible.stream().map(this::toSummary).toList(), total);
    }

    @Transactional
    public Created create(CurrentUser actor, String username, String displayName, List<String> roles,
            DataScope scope) {
        checkGrantable(actor, roles, scope);
        users.findByUsername(username).ifPresent(existing -> {
            throw ApiException.conflict("账号已存在", username);
        });

        String password = randomPassword();
        UserEntity entity = new UserEntity(Ids.next("usr"), actor.tenantId(), username, displayName,
                passwordEncoder.encode(password), true, scope.toJson());
        users.save(entity);
        roles.forEach(role -> userRoles.save(new UserRoleEntity(entity.getId(), role)));

        audit.record(actor, AuditLog.USER_CREATE, "user", entity.getId(),
                Map.of("username", username, "roles", roles, "data_scope", scope.toJson()));
        return new Created(toSummary(entity, roles), password);
    }

    @Transactional
    public Summary updateRoles(CurrentUser actor, String userId, List<String> roles, DataScope scope,
            int revision) {
        checkGrantable(actor, roles, scope);
        UserEntity entity = users.findById(userId)
                .filter(user -> user.getTenantId().equals(actor.tenantId()))
                .filter(user -> visibleTo(actor, user))
                .orElseThrow(() -> ApiException.notFound("账号不存在"));
        if (entity.getRevision() != revision) {
            throw ApiException.conflict("账号已在别处修改", "当前 revision 为 " + entity.getRevision());
        }

        List<String> before = userRoles.findRoles(userId);
        Map<String, Object> beforeScope = entity.getDataScope();
        // 先改用户行再重写角色：deleteByUserId 是会清空持久化上下文的批量删除，
        // 放在它后面改 entity 等于改一个已经游离的对象，范围与版本号都不会落库。
        entity.applyScope(scope.toJson());
        entity.bumpRevision();
        userRoles.deleteByUserId(userId);
        roles.forEach(role -> userRoles.save(new UserRoleEntity(userId, role)));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("roles_before", before);
        detail.put("roles_after", roles);
        detail.put("scope_before", beforeScope);
        detail.put("scope_after", scope.toJson());
        audit.record(actor, AuditLog.USER_ROLES_UPDATE, "user", userId, detail);
        return toSummary(entity, roles);
    }

    /** 授出的角色权限必须是操作者权限的子集，数据范围也不能比操作者宽。 */
    private static void checkGrantable(CurrentUser actor, List<String> roles, DataScope scope) {
        Set<String> granted;
        try {
            granted = Role.permissionsOf(roles);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, exception.getMessage());
        }
        if (!actor.can(Permissions.ALL)) {
            List<String> exceeding = granted.stream().filter(permission -> !actor.can(permission)).sorted().toList();
            if (!exceeding.isEmpty()) {
                throw ApiException.forbidden("不能授出自己没有的权限", "越界权限：" + String.join(", ", exceeding));
            }
        }
        checkScopeWithin(actor.dataScope().agencies(), scope.agencies(), "代理");
        checkScopeWithin(actor.dataScope().products(), scope.products(), "产品");
        checkScopeWithin(actor.dataScope().operators(), scope.operators(), "运营");
    }

    private static void checkScopeWithin(List<String> actorValues, List<String> grantedValues, String label) {
        if (actorValues == null) {
            return;
        }
        if (grantedValues == null) {
            throw ApiException.forbidden("不能授出不限范围的" + label, "自己的范围是：" + String.join(", ", actorValues));
        }
        List<String> exceeding = grantedValues.stream().filter(value -> !actorValues.contains(value)).toList();
        if (!exceeding.isEmpty()) {
            throw ApiException.forbidden("不能授出超出自己范围的" + label, "越界取值：" + String.join(", ", exceeding));
        }
    }

    private static boolean visibleTo(CurrentUser actor, UserEntity user) {
        DataScope target = DataScope.fromJson(user.getDataScope());
        return visibleWithin(actor.dataScope().agencies(), target.agencies())
                && visibleWithin(actor.dataScope().products(), target.products())
                && visibleWithin(actor.dataScope().operators(), target.operators());
    }

    private static boolean visibleWithin(List<String> actorValues, List<String> targetValues) {
        if (actorValues == null) {
            return true;
        }
        // 保持受限管理者只能管理有明确归属的账号；不限或空归属由上级管理员处理。
        return targetValues != null && !targetValues.isEmpty() && actorValues.containsAll(targetValues);
    }

    private Summary toSummary(UserEntity entity) {
        return toSummary(entity, userRoles.findRoles(entity.getId()));
    }

    private Summary toSummary(UserEntity entity, List<String> roles) {
        return new Summary(entity.getId(), entity.getUsername(), entity.getDisplayName(), roles,
                entity.getStatus(), DataScope.fromJson(entity.getDataScope()), entity.getRevision());
    }

    private static String randomPassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int index = 0; index < PASSWORD_LENGTH; index++) {
            password.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    public record Summary(String id, String username, String displayName, List<String> roles, String status,
            DataScope dataScope, int revision) {
    }

    public record Page(List<Summary> items, long total) {
    }

    public record Created(Summary user, String oneTimePassword) {
    }

    /** 供控制器把请求里的三个范围字段收成一个对象。 */
    public static DataScope scopeOf(List<String> agencies, List<String> products, List<String> operators) {
        return new DataScope(agencies, products, operators);
    }

    /** 列表查询用到的角色反查，避免控制器直接碰仓库。 */
    public List<String> rolesOf(String userId) {
        return new ArrayList<>(userRoles.findRoles(userId));
    }
}
