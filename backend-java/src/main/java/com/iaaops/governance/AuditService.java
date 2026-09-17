package com.iaaops.governance;

import com.iaaops.governance.persistence.AuditEventEntity;
import com.iaaops.governance.persistence.AuditEventRepository;
import com.iaaops.iam.domain.CurrentUser;
import com.iaaops.shared.error.ApiException;
import com.iaaops.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 审计日志查询：按时间倒序游标分页。 */
@Service
public class AuditService {

    private final AuditEventRepository events;

    AuditService(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page list(CurrentUser user, String action, String cursor, int limit) {
        Cursor from = Cursor.decode(cursor);
        // 多取一条判断还有没有下一页，避免再跑一次 count
        List<AuditEventEntity> rows = events.page(user.tenantId(), action, from.occurredAt(), from.id(),
                Limit.of(limit + 1));
        boolean more = rows.size() > limit;
        List<AuditEventEntity> items = more ? rows.subList(0, limit) : rows;
        String next = more ? new Cursor(items.getLast().getOccurredAt(), items.getLast().getId()).encode() : null;
        return new Page(items.stream().map(AuditService::toEvent).toList(), next);
    }

    private static Event toEvent(AuditEventEntity entity) {
        return new Event(entity.getId(), entity.getOccurredAt(), entity.getActor(), entity.getAction(),
                entity.getTargetType(), entity.getTargetId(), entity.getDetail());
    }

    public record Event(String id, OffsetDateTime occurredAt, String actor, String action, String targetType,
            String targetId, Map<String, Object> detail) {
    }

    public record Page(List<Event> items, String nextCursor) {
    }

    /** 游标就是上一页最后一条的 (时间, id)，编码成一个不透明字符串。 */
    record Cursor(OffsetDateTime occurredAt, String id) {

        static final Cursor START = new Cursor(null, null);

        String encode() {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((occurredAt + "|" + id).getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String raw) {
            if (raw == null || raw.isEmpty()) {
                return START;
            }
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
                String[] parts = decoded.split("\\|", 2);
                return new Cursor(OffsetDateTime.parse(parts[0]), parts[1]);
            } catch (RuntimeException exception) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "游标无效", "请从第一页重新开始");
            }
        }
    }
}
