package com.iaaops.governance;

import com.iaaops.shared.Ids;
import com.iaaops.governance.persistence.AuditEventEntity;
import com.iaaops.governance.persistence.AuditEventRepository;
import com.iaaops.iam.domain.CurrentUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 写操作留痕。
 *
 * 只追加，不修改也不删除——能改的记录不叫审计。写入跟随业务事务：业务回滚了，
 * 审计也不应留下"发生过"的假象。
 */
@Service
public class AuditLog {

    /** 动作名用「对象.动作」，便于按前缀筛选。 */
    public static final String MAPPING_UPSERT = "mapping.upsert";
    public static final String USER_CREATE = "user.create";
    public static final String USER_ROLES_UPDATE = "user.roles.update";
    public static final String IMPORT_CREATE = "import.create";

    private final AuditEventRepository events;

    AuditLog(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void record(CurrentUser actor, String action, String targetType, String targetId,
            Map<String, Object> detail) {
        events.save(new AuditEventEntity(Ids.next("aud"), actor.tenantId(), actor.username(), action, targetType,
                targetId, detail == null ? Map.of() : detail));
    }

    /** 记录前后值：审计的价值在于"改成了什么"，只记动作等于没记。 */
    public static Map<String, Object> change(String field, Object before, Object after) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("field", field);
        detail.put("before", before);
        detail.put("after", after);
        return detail;
    }
}
