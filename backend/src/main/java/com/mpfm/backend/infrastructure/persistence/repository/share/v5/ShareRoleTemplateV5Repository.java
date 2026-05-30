package com.mpfm.backend.infrastructure.persistence.repository.share.v5;

import com.mpfm.backend.infrastructure.persistence.entity.share.v5.ShareRoleTemplateV5Entity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * v5 角色模板仓储，提供按挂载、角色与状态查询能力。
 */
public interface ShareRoleTemplateV5Repository extends JpaRepository<ShareRoleTemplateV5Entity, UUID> {
    Optional<ShareRoleTemplateV5Entity> findByMountIdAndName(UUID mountId, String name);
    Optional<ShareRoleTemplateV5Entity> findByRoleId(UUID roleId);
    List<ShareRoleTemplateV5Entity> findByMountId(UUID mountId);
    List<ShareRoleTemplateV5Entity> findByRoleIdIn(Collection<UUID> roleIds);
}
