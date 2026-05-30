package com.mpfm.backend.infrastructure.persistence.repository.share.v5;

import com.mpfm.backend.infrastructure.persistence.entity.share.v5.ShareRoleTemplatePrivilegeV5Entity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * v5 角色模板特权仓储，提供模板与目标路径维度查询能力。
 */
public interface ShareRoleTemplatePrivilegeV5Repository extends JpaRepository<ShareRoleTemplatePrivilegeV5Entity, UUID> {
    Optional<ShareRoleTemplatePrivilegeV5Entity> findByTemplateIdAndTargetPath(UUID templateId, String targetPath);
    Optional<ShareRoleTemplatePrivilegeV5Entity> findByMountIdAndTargetPath(UUID mountId, String targetPath);
    List<ShareRoleTemplatePrivilegeV5Entity> findByTemplateId(UUID templateId);
    List<ShareRoleTemplatePrivilegeV5Entity> findByMountId(UUID mountId);
    List<ShareRoleTemplatePrivilegeV5Entity> findByTemplateIdIn(Collection<UUID> templateIds);
    List<ShareRoleTemplatePrivilegeV5Entity> findByTemplateIdAndTargetPathIn(UUID templateId, Collection<String> targetPaths);
    List<ShareRoleTemplatePrivilegeV5Entity> findByTemplateIdInAndTargetPathIn(Collection<UUID> templateIds, Collection<String> targetPaths);
}
