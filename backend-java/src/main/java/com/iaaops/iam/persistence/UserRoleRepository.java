package com.iaaops.iam.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleEntity.Key> {

    @Query("select r.role from UserRoleEntity r where r.userId = :userId order by r.role")
    List<String> findRoles(@Param("userId") String userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserRoleEntity r where r.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);
}
