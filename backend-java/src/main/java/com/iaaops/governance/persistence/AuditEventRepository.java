package com.iaaops.governance.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {

    /**
     * 按 (occurred_at, id) 倒序取一页。
     *
     * 游标带上 id 而不只有时间：同一毫秒内写入多条时，只按时间翻页会漏记录或重复记录。
     */
    @Query("""
            select e from AuditEventEntity e
            where e.tenantId = :tenantId
              and (cast(:action as String) is null or e.action = :action)
              and (cast(:beforeId as String) is null or e.occurredAt < :beforeAt
                   or (e.occurredAt = :beforeAt and e.id < :beforeId))
            order by e.occurredAt desc, e.id desc
            """)
    List<AuditEventEntity> page(@Param("tenantId") String tenantId, @Param("action") String action,
            @Param("beforeAt") OffsetDateTime beforeAt, @Param("beforeId") String beforeId, Limit limit);
}
