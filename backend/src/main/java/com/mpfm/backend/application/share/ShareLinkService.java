package com.mpfm.backend.application.share;

import com.mpfm.backend.application.user.PlatformRole;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.ShareLinkEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.ShareRoleEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.SharedMountAccessEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ShareLinkService {
    private static final String ROLE_OWNER = "owner";
    private static final String STATE_ACTIVE = "active";

    private final ShareRepositories repositories;
    private final ShareAccessSupport support;
    private final ShareRoleService roleService;

    ShareLinkService(ShareRepositories repositories, ShareAccessSupport support, ShareRoleService roleService) {
        this.repositories = repositories;
        this.support = support;
        this.roleService = roleService;
    }

    ShareApplicationService.ShareLinkResult createLink(String operator, UUID mountId, UUID roleId, OffsetDateTime startAt,
                                                       OffsetDateTime expireAt, Integer maxUses,
                                                       OffsetDateTime roleStartAt, OffsetDateTime roleExpireAt) {
        UserEntity op = support.requireUser(operator);
        MountEntity mount = support.requireOwnedOrAdminMount(op, mountId);
        roleService.ensureBuiltInRoles(mount);
        var role = support.requireRole(roleId);
        if (!role.getMountId().equals(mountId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "role not match mount");
        }
        if (ROLE_OWNER.equalsIgnoreCase(role.getName())) {
            throw new BusinessException(ErrorCode.OWNER_IMMUTABLE, "owner can not be granted");
        }
        ShareLinkEntity link = new ShareLinkEntity();
        link.setId(UUID.randomUUID());
        link.setMountId(mountId);
        link.setRoleId(roleId);
        link.setToken(UUID.randomUUID().toString().replace("-", ""));
        link.setState(STATE_ACTIVE);
        link.setStartAt(startAt);
        link.setExpireAt(expireAt);
        link.setMaxUses(maxUses);
        link.setUsedCount(0);
        link.setRoleStartAt(roleStartAt);
        link.setRoleExpireAt(roleExpireAt);
        link.setCreatedByUserId(op.getId());
        link.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        link.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repositories.shareLinkRepository.save(link);
        return ShareApplicationService.toLink(link);
    }

    List<ShareApplicationService.ShareLinkResult> listLinks(String operator) {
        UserEntity op = support.requireUser(operator);
        return (op.getPlatformRole() == PlatformRole.USER
                ? repositories.shareLinkRepository.findAll().stream().filter(i -> i.getCreatedByUserId().equals(op.getId()))
                : repositories.shareLinkRepository.findAll().stream()).map(ShareApplicationService::toLink).toList();
    }

    ShareApplicationService.ShareLinkResult getLink(String operator, UUID linkId) {
        UserEntity op = support.requireUser(operator);
        ShareLinkEntity link = support.requireLink(linkId);
        support.requireLinkOwnerOrAdmin(op, link);
        return ShareApplicationService.toLink(link);
    }

    ShareApplicationService.ShareLinkResult updateLink(String operator, UUID linkId, OffsetDateTime startAt, OffsetDateTime expireAt, Integer maxUses) {
        UserEntity op = support.requireUser(operator);
        ShareLinkEntity link = support.requireLink(linkId);
        support.requireLinkOwnerOrAdmin(op, link);
        link.setStartAt(startAt);
        link.setExpireAt(expireAt);
        link.setMaxUses(maxUses);
        link.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repositories.shareLinkRepository.save(link);
        return ShareApplicationService.toLink(link);
    }

    ShareApplicationService.ShareLinkResult revokeLink(String operator, UUID linkId) {
        UserEntity op = support.requireUser(operator);
        ShareLinkEntity link = support.requireLink(linkId);
        support.requireLinkOwnerOrAdmin(op, link);
        link.setState("revoked");
        link.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repositories.shareLinkRepository.save(link);
        return ShareApplicationService.toLink(link);
    }

    void deleteLink(String operator, UUID linkId) {
        UserEntity op = support.requireUser(operator);
        ShareLinkEntity link = support.requireLink(linkId);
        support.requireLinkOwnerOrAdmin(op, link);
        repositories.shareLinkRepository.delete(link);
    }

    ShareApplicationService.ResolveResult resolveLink(String operator, String token) {
        UserEntity user = support.requireUser(operator);
        ShareLinkEntity link = repositories.shareLinkRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.LINK_INVALID, "invalid"));
        validateLink(link);
        SharedMountAccessEntity access = repositories.sharedMountAccessRepository
                .findByUserIdAndMountIdAndRoleId(user.getId(), link.getMountId(), link.getRoleId())
                .orElseGet(() -> createAccess(user, link));
        List<SharedMountAccessEntity> all = repositories.sharedMountAccessRepository.findByUserIdAndMountId(user.getId(), link.getMountId());
        for (SharedMountAccessEntity row : all) {
            row.setActive(false);
            row.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
        access.setActive(true);
        access.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        if (all.stream().noneMatch(row -> row.getId().equals(access.getId()))) {
            all.add(access);
        }
        repositories.sharedMountAccessRepository.saveAll(all);

        link.setUsedCount(link.getUsedCount() + 1);
        link.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repositories.shareLinkRepository.save(link);
        return new ShareApplicationService.ResolveResult(link.getMountId(), link.getRoleId(), link.getState(), link.getToken());
    }

    ShareApplicationService.ResolveResult switchRole(String operator, UUID mountId, UUID roleId) {
        UserEntity user = support.requireUser(operator);
        List<SharedMountAccessEntity> accesses = repositories.sharedMountAccessRepository.findByUserIdAndMountId(user.getId(), mountId);
        SharedMountAccessEntity target = accesses.stream().filter(a -> a.getRoleId().equals(roleId)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_DENIED, "role not granted"));
        for (SharedMountAccessEntity access : accesses) {
            access.setActive(access.getId().equals(target.getId()));
            access.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
        repositories.sharedMountAccessRepository.saveAll(accesses);
        return new ShareApplicationService.ResolveResult(mountId, roleId, "active", null);
    }

    private SharedMountAccessEntity createAccess(UserEntity user, ShareLinkEntity link) {
        SharedMountAccessEntity row = new SharedMountAccessEntity();
        row.setId(UUID.randomUUID());
        row.setUserId(user.getId());
        row.setMountId(link.getMountId());
        row.setRoleId(link.getRoleId());
        row.setActive(true);
        row.setGrantedByLinkId(link.getId());
        row.setGrantedAt(OffsetDateTime.now(ZoneOffset.UTC));
        row.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return row;
    }

    private void validateLink(ShareLinkEntity link) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        validateLinkWindow(link, now);
        var role = support.requireRole(link.getRoleId());
        validateRoleWindow(link, role, now);
    }

    private void validateLinkWindow(ShareLinkEntity link, OffsetDateTime now) {
        if (!STATE_ACTIVE.equals(link.getState())) {
            throw new BusinessException(ErrorCode.LINK_REVOKED, "revoked");
        }
        if (link.getStartAt() != null && now.isBefore(link.getStartAt())) {
            throw new BusinessException(ErrorCode.LINK_INVALID, "not started");
        }
        if (link.getExpireAt() != null && now.isAfter(link.getExpireAt())) {
            throw new BusinessException(ErrorCode.LINK_EXPIRED, "expired");
        }
        if (link.getMaxUses() != null && link.getUsedCount() >= link.getMaxUses()) {
            throw new BusinessException(ErrorCode.LINK_EXHAUSTED, "exhausted");
        }
    }

    private void validateRoleWindow(ShareLinkEntity link, ShareRoleEntity role, OffsetDateTime now) {
        if (support.isRoleUnavailable(role, now)) {
            throw new BusinessException(ErrorCode.ROLE_EXPIRED, "expired");
        }
        if (link.getRoleStartAt() != null && now.isBefore(link.getRoleStartAt())) {
            throw new BusinessException(ErrorCode.ROLE_EXPIRED, "not started");
        }
        if (link.getRoleExpireAt() != null && now.isAfter(link.getRoleExpireAt())) {
            throw new BusinessException(ErrorCode.ROLE_EXPIRED, "expired");
        }
    }
}


