package com.mpfm.backend.infrastructure.persistence.repository.share.v5;

import com.mpfm.backend.infrastructure.persistence.entity.share.v5.SharedMountAccessV5Entity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * v5 授权关系仓储，提供用户-挂载-角色维度查询能力。
 */
public interface SharedMountAccessV5Repository extends JpaRepository<SharedMountAccessV5Entity, UUID> {
    Optional<SharedMountAccessV5Entity> findByUserIdAndMountIdAndRoleId(UUID userId, UUID mountId, UUID roleId);
    List<SharedMountAccessV5Entity> findByUserIdAndMountId(UUID userId, UUID mountId);
    List<SharedMountAccessV5Entity> findByUserIdAndActiveTrue(UUID userId);
    List<SharedMountAccessV5Entity> findByMountIdAndActiveTrue(UUID mountId);
}
