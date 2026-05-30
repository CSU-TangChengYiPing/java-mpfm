package com.mpfm.backend.infrastructure.persistence.repository;

import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.application.user.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 用户仓储接口，提供按用户名/角色/状态查询与管理端条件检索能力。
 */
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByUsername(String username);
    List<UserEntity> findByPlatformRoleOrderByAuditUpdatedAtDesc(com.mpfm.backend.application.user.PlatformRole role);
    boolean existsByUsername(String username);
    long countByPlatformRole(com.mpfm.backend.application.user.PlatformRole role);
    List<UserEntity> findByStatus(UserStatus status);

    @Query("""
            select u from UserEntity u
            where (coalesce(:username, '') = '' or lower(u.username) like lower(concat('%', :username, '%')))
              and (coalesce(:displayName, '') = '' or lower(u.profile.displayName) like lower(concat('%', :displayName, '%')))
              and (:status is null or u.status = :status)
            """)
    List<UserEntity> search(@Param("username") String username,
                            @Param("displayName") String displayName,
                            @Param("status") UserStatus status);
}





