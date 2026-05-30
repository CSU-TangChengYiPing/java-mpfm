package com.mpfm.backend.infrastructure.persistence.repository.share;

import com.mpfm.backend.infrastructure.persistence.entity.share.ShareRolePolicyEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 共享角色策略仓储接口，提供按角色查询与整组策略删除能力。
 */
public interface ShareRolePolicyRepository extends JpaRepository<ShareRolePolicyEntity, UUID> {
    List<ShareRolePolicyEntity> findByRoleId(UUID roleId);
    List<ShareRolePolicyEntity> findByRoleIdIn(Collection<UUID> roleIds);
    void deleteByRoleId(UUID roleId);
}





