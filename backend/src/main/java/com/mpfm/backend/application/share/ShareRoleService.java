package com.mpfm.backend.application.share;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.ShareRoleEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.ShareRolePolicyEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ShareRoleService {
    private static final String ROLE_OWNER = "owner";
    private static final String ROLE_VISITOR = "visitor";
    private static final String ROLE_COLLABORATOR = "collaborator";
    private static final String STATE_ACTIVE = "active";
    private static final String STATE_DISABLED = "disabled";
    private static final String MESSAGE_SYSTEM_ROLE_IMMUTABLE = "system role immutable";

    private final ShareRepositories repositories;
    private final ShareAccessSupport support;
    private final SharePathPolicySupport pathPolicySupport;

    ShareRoleService(ShareRepositories repositories, ShareAccessSupport support, SharePathPolicySupport pathPolicySupport) {
        this.repositories = repositories;
        this.support = support;
        this.pathPolicySupport = pathPolicySupport;
    }

    ShareApplicationService.ShareRoleResult createRole(String operator, UUID mountId, String name, OffsetDateTime roleExpiresAt) {
        var op = support.requireUser(operator);
        MountEntity mount = support.requireOwnedOrAdminMount(op, mountId);
        ensureBuiltInRoles(mount);
        if (ROLE_OWNER.equalsIgnoreCase(name)) {
            throw new BusinessException(ErrorCode.OWNER_IMMUTABLE, "owner immutable");
        }
        repositories.shareRoleRepository.findByMountIdAndName(mountId, name).ifPresent(r -> {
            throw new BusinessException(ErrorCode.CONFLICT, "role exists");
        });
        ShareRoleEntity role = new ShareRoleEntity();
        role.setId(UUID.randomUUID());
        role.setMountId(mountId);
        role.setCreatorUserId(op.getId());
        role.setName(name);
        role.setState(STATE_ACTIVE);
        role.setSystem(false);
        role.setRoleExpiresAt(roleExpiresAt);
        role.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        role.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repositories.shareRoleRepository.save(role);
        return ShareApplicationService.toRole(role);
    }

    List<ShareApplicationService.ShareRoleResult> listRoles(String operator, UUID mountId) {
        var op = support.requireUser(operator);
        MountEntity mount = support.requireOwnedOrAdminMount(op, mountId);
        ensureBuiltInRoles(mount);
        return repositories.shareRoleRepository.findByMountId(mountId).stream().map(ShareApplicationService::toRole).toList();
    }

    ShareApplicationService.ShareRoleResult updateRole(String operator, UUID roleId, String name, OffsetDateTime roleExpiresAt) {
        var op = support.requireUser(operator);
        ShareRoleEntity role = support.requireRole(roleId);
        support.requireOwnedOrAdminMount(op, role.getMountId());
        assertRoleMutable(role);
        role.setName(name);
        role.setRoleExpiresAt(roleExpiresAt);
        role.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repositories.shareRoleRepository.save(role);
        return ShareApplicationService.toRole(role);
    }

    ShareApplicationService.ShareRoleResult disableRole(String operator, UUID roleId) {
        var op = support.requireUser(operator);
        ShareRoleEntity role = support.requireRole(roleId);
        support.requireOwnedOrAdminMount(op, role.getMountId());
        assertRoleMutable(role);
        role.setState(STATE_DISABLED);
        role.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repositories.shareRoleRepository.save(role);
        return ShareApplicationService.toRole(role);
    }

    void deleteRole(String operator, UUID roleId) {
        var op = support.requireUser(operator);
        ShareRoleEntity role = support.requireRole(roleId);
        support.requireOwnedOrAdminMount(op, role.getMountId());
        assertRoleMutable(role);
        repositories.shareRolePolicyRepository.deleteByRoleId(roleId);
        repositories.shareRoleRepository.delete(role);
    }

    List<ShareApplicationService.PathPolicyResult> updatePolicies(String operator, UUID roleId, List<ShareApplicationService.PathPolicyCommand> policies) {
        var op = support.requireUser(operator);
        ShareRoleEntity role = support.requireRole(roleId);
        support.requireOwnedOrAdminMount(op, role.getMountId());
        assertRoleMutable(role);
        for (ShareApplicationService.PathPolicyCommand policy : policies) {
            if (policy.canWrite() && !policy.canRead()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "write requires read");
            }
        }
        repositories.shareRolePolicyRepository.deleteByRoleId(roleId);
        List<ShareRolePolicyEntity> rows = policies.stream().map(p -> {
            ShareRolePolicyEntity entity = new ShareRolePolicyEntity();
            entity.setId(UUID.randomUUID());
            entity.setRoleId(roleId);
            entity.setPathPattern(pathPolicySupport.normalizePolicyPath(p.pathPattern()));
            entity.setCanVisible(p.canVisible());
            entity.setCanRead(p.canRead());
            entity.setCanWrite(p.canWrite());
            entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return entity;
        }).toList();
        repositories.shareRolePolicyRepository.saveAll(rows);
        return rows.stream().map(ShareApplicationService::toPolicy).toList();
    }

    void ensureBuiltInRoles(MountEntity mount) {
        ShareRoleEntity owner = repositories.shareRoleRepository.findByMountIdAndName(mount.getId(), ROLE_OWNER).orElse(null);
        if (owner == null) {
            owner = newSystemRole(mount, ROLE_OWNER);
            repositories.shareRoleRepository.save(owner);
        }
        if (repositories.shareRolePolicyRepository.findByRoleId(owner.getId()).isEmpty()) {
            pathPolicySupport.upsertSinglePolicy(owner.getId(), "/", true, true, true);
        }
        ShareRoleEntity visitor = repositories.shareRoleRepository.findByMountIdAndName(mount.getId(), ROLE_VISITOR).orElse(null);
        if (visitor == null) {
            visitor = newSystemRole(mount, ROLE_VISITOR);
            repositories.shareRoleRepository.save(visitor);
        }
        if (repositories.shareRolePolicyRepository.findByRoleId(visitor.getId()).isEmpty()) {
            pathPolicySupport.upsertSinglePolicy(visitor.getId(), "/", true, true, false);
        }
        ShareRoleEntity collaborator = repositories.shareRoleRepository.findByMountIdAndName(mount.getId(), ROLE_COLLABORATOR).orElse(null);
        if (collaborator == null) {
            collaborator = newSystemRole(mount, ROLE_COLLABORATOR);
            repositories.shareRoleRepository.save(collaborator);
        }
        if (repositories.shareRolePolicyRepository.findByRoleId(collaborator.getId()).isEmpty()) {
            pathPolicySupport.upsertSinglePolicy(collaborator.getId(), "/", true, true, true);
        }
    }

    private ShareRoleEntity newSystemRole(MountEntity mount, String name) {
        ShareRoleEntity role = new ShareRoleEntity();
        role.setId(UUID.randomUUID());
        role.setMountId(mount.getId());
        role.setCreatorUserId(mount.getOwnerId());
        role.setName(name);
        role.setState(STATE_ACTIVE);
        role.setSystem(true);
        role.setRoleExpiresAt(null);
        role.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        role.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return role;
    }

    private void assertRoleMutable(ShareRoleEntity role) {
        if (role.isSystem()) {
            throw new BusinessException(ErrorCode.OWNER_IMMUTABLE, MESSAGE_SYSTEM_ROLE_IMMUTABLE);
        }
    }
}


