package com.iaaops.mapping.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountMappingRepository
        extends JpaRepository<AccountMappingEntity, AccountMappingEntity.Key> {

    String FILTER = """
            from AccountMappingEntity m
            where m.tenantId = :tenantId
              and (cast(:media as String) is null or m.media = :media)
              and (cast(:keyword as String) is null
                   or lower(m.account) like lower(concat('%', cast(:keyword as String), '%'))
                   or lower(coalesce(m.product, '')) like lower(concat('%', cast(:keyword as String), '%'))
                   or lower(coalesce(m.agency, '')) like lower(concat('%', cast(:keyword as String), '%'))
                   or lower(coalesce(m.operator, '')) like lower(concat('%', cast(:keyword as String), '%')))
            """;

    @Query("select m " + FILTER + " order by m.media, m.account")
    List<AccountMappingEntity> page(@Param("tenantId") String tenantId, @Param("media") String media,
            @Param("keyword") String keyword, Pageable pageable);

    @Query("select count(m) " + FILTER)
    long countMatching(@Param("tenantId") String tenantId, @Param("media") String media,
            @Param("keyword") String keyword);

    /** 批量写入前逐条取行锁，避免两个人同时改同一条时后写的悄悄覆盖前一个。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m from AccountMappingEntity m
            where m.tenantId = :tenantId and m.media = :media and m.account = :account
            """)
    Optional<AccountMappingEntity> lock(@Param("tenantId") String tenantId, @Param("media") String media,
            @Param("account") String account);
}
