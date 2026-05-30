package com.mpfm.backend.infrastructure.persistence.repository.share;

import com.mpfm.backend.infrastructure.persistence.entity.share.ShareRoleEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 共享角色仓储接口，提供按挂载查询角色及角色名唯一性校验查询。
 */
public interface ShareRoleRepository extends JpaRepository<ShareRoleEntity, UUID> {
    List<ShareRoleEntity> findByMountId(UUID mountId);
    Optional<ShareRoleEntity> findByMountIdAndName(UUID mountId, String name);
}





