package com.mpfm.backend.application.share;

import com.mpfm.backend.application.user.PlatformRole;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.ShareLinkEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.ShareRoleEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ShareAccessSupport {
    private static final String STATE_ACTIVE = "active";
    private static final String STATE_DISABLED = "disabled";

    private final ShareRepositories repositories;

    ShareAccessSupport(ShareRepositories repositories) {
        this.repositories = repositories;
    }

    UserEntity requireUser(String username) {
        return repositories.userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID, "user not found"));
    }

    MountEntity requireOwnedOrAdminMount(UserEntity operator, UUID mountId) {
        MountEntity mount = repositories.mountRepository.findById(mountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
        if (operator.getPlatformRole() != PlatformRole.USER || mount.getOwnerId().equals(operator.getId())) {
            return mount;
        }
        throw new BusinessException(ErrorCode.PERMISSION_DENIED, "mount denied");
    }

    ShareRoleEntity requireRole(UUID roleId) {
        return repositories.shareRoleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "role not found"));
    }

    ShareLinkEntity requireLink(UUID linkId) {
        return repositories.shareLinkRepository.findById(linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "link not found"));
    }

    void requireLinkOwnerOrAdmin(UserEntity operator, ShareLinkEntity link) {
        if (operator.getPlatformRole() == PlatformRole.USER && !link.getCreatedByUserId().equals(operator.getId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "not owner");
        }
    }

    boolean isRoleUnavailable(ShareRoleEntity role, OffsetDateTime now) {
        return STATE_DISABLED.equals(role.getState())
                || (role.getRoleExpiresAt() != null && now.isAfter(role.getRoleExpiresAt()));
    }

    boolean isOwnerOrAdminPermission(ShareApplicationService.EffectivePermissionResult permission) {
        return permission.canVisible() && permission.canRead() && permission.canWrite() && permission.roleId() == null;
    }
}


