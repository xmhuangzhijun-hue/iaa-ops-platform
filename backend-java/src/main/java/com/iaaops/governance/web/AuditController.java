package com.iaaops.governance.web;

import com.iaaops.governance.AuditService;
import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.iam.domain.Permissions;
import com.iaaops.shared.error.ApiException;
import com.iaaops.shared.error.ErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuditController {

    private final AuditService audit;

    AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/audit-events")
    public AuditPage list(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) @Size(max = 64) String action,
            @RequestParam(required = false) @Size(max = 128) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        if (!Permissions.has(user.permissions(), Permissions.AUDIT_READ)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "没有该操作的权限");
        }
        AuditService.Page page = audit.list(user, action, cursor, limit);
        return new AuditPage(page.items().stream().map(AuditController::toItem).toList(), page.nextCursor());
    }

    private static Item toItem(AuditService.Event event) {
        return new Item(event.id(), event.occurredAt(), event.actor(), event.action(), event.targetType(),
                event.targetId(), event.detail());
    }

    public record Item(String id, OffsetDateTime occurredAt, String actor, String action, String targetType,
            String targetId, Map<String, Object> detail) {
    }

    public record AuditPage(List<Item> items, String nextCursor) {
    }
}
