package com.iaaops.iam.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    String FILTER = """
            from UserEntity u
            where u.tenantId = :tenantId
              and (cast(:status as String) is null or u.status = :status)
              and (cast(:keyword as String) is null
                   or lower(u.username) like lower(concat('%', cast(:keyword as String), '%'))
                   or lower(u.displayName) like lower(concat('%', cast(:keyword as String), '%')))
            """;

    Optional<UserEntity> findByUsername(String username);

    @Query("select u " + FILTER + " order by u.createdAt desc, u.id")
    List<UserEntity> page(@Param("tenantId") String tenantId, @Param("keyword") String keyword,
            @Param("status") String status, Pageable pageable);

    @Query("select count(u) " + FILTER)
    long countMatching(@Param("tenantId") String tenantId, @Param("keyword") String keyword,
            @Param("status") String status);
}
