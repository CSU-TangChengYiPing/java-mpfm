package com.mpfm.backend.infrastructure.persistence.repository.share;

import com.mpfm.backend.infrastructure.persistence.entity.share.SharedMountAccessEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 共享挂载访问仓储接口，提供按用户/挂载/角色维度查询访问关系能力。
 */
public interface SharedMountAccessRepository extends JpaRepository<SharedMountAccessEntity, UUID> {
    List<SharedMountAccessEntity> findByUserIdAndMountId(UUID userId, UUID mountId);
    Optional<SharedMountAccessEntity> findByUserIdAndMountIdAndRoleId(UUID userId, UUID mountId, UUID roleId);
}





