package com.iaaops.ingestion.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ImportTaskRepository extends JpaRepository<ImportTaskEntity, String> {

    Optional<ImportTaskEntity> findByIdAndTenantId(String id, String tenantId);

    /**
     * 库里的当前时刻。
     *
     * created_at 由数据库默认值写入，finished_at 若取 JVM 时钟，两个时间戳就来自两个钟——
     * 容器与宿主机差零点几秒就会出现"完成早于创建"。用 clock_timestamp() 而不是 now()：
     * 后者返回事务开始时间，拿到的是处理开始而不是处理结束。
     */
    @Query(value = "select clock_timestamp()", nativeQuery = true)
    Instant databaseNow();
}
