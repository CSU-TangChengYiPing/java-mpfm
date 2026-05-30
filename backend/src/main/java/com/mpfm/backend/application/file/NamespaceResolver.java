package com.mpfm.backend.application.file;

import com.mpfm.backend.application.share.v5.ShareAuthorizationV5Service;
import com.mpfm.backend.application.user.PlatformRole;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.repository.MountRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 虚拟命名空间解析器：把统一 virtualPath 解析为挂载、相对路径与权限上下文。
 */
@Component
public class NamespaceResolver {
    private static final String STATE_ENABLED = "enabled";
    private static final String PERSONAL_PREFIX = "/personal/";
    private static final String SHARED_PREFIX = "/shared/";
    private static final String SHARED_ALIAS_DELIMITER = "---";

    private final MountRepository mountRepository;
    private final ShareAuthorizationV5Service shareAuthorizationV5Service;
    private final UserRepository userRepository;

    public NamespaceResolver(MountRepository mountRepository,
                             ShareAuthorizationV5Service shareAuthorizationV5Service,
                             UserRepository userRepository) {
        this.mountRepository = mountRepository;
        this.shareAuthorizationV5Service = shareAuthorizationV5Service;
        this.userRepository = userRepository;
    }

    public ResolveResult resolve(String username, String virtualPath, boolean requireWrite, boolean requireRead) {
        UserEntity operator = requireUser(username);
        String normalized = normalizeVirtualPath(virtualPath);
        boolean shared = normalized.startsWith(SHARED_PREFIX);
        String remainder = normalized.substring(shared ? SHARED_PREFIX.length() : PERSONAL_PREFIX.length());
        String[] parts = remainder.split("/", 2);
        MountEntity mount = resolveMountReference(username, shared, parts[0]);
        UUID mountId = mount.getId();
        String relPath = parts.length > 1 && !parts[1].isBlank() ? parts[1] : ".";
        if (relPath.contains("..")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid virtual path");
        }
        if (!STATE_ENABLED.equalsIgnoreCase(mount.getState())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "mount disabled");
        }
        String permissionPath = toPermissionPath(shared, mount.getName(), relPath);
        ShareAuthorizationV5Service.EffectivePermissionResult permission =
                shareAuthorizationV5Service.effective(username, mountId, permissionPath);
        boolean ownerOrAdmin = isOwnerOrAdmin(operator, mount);
        if (!ownerOrAdmin) {
            if (!permission.canVisible()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "path not found");
            }
            if (requireRead && !permission.canRead()) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, "path not readable");
            }
            if (requireWrite && (!permission.canRead() || !permission.canWrite())) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, "path not writable");
            }
        }
        return new ResolveResult(mount, relPath, normalized, shared, ownerOrAdmin);
    }

    private UserEntity requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID, "user not found"));
    }

    private boolean isOwnerOrAdmin(UserEntity operator, MountEntity mount) {
        if (operator.getId() != null && operator.getId().equals(mount.getOwnerId())) {
            return true;
        }
        PlatformRole role = operator.getPlatformRole();
        return role != null && role != PlatformRole.USER;
    }

    private String normalizeVirtualPath(String virtualPath) {
        String path = (virtualPath == null || virtualPath.isBlank()) ? "/" : virtualPath.trim().replace('\\', '/');
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith(PERSONAL_PREFIX) && !path.startsWith(SHARED_PREFIX)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid virtual path");
        }
        return path;
    }

    private MountEntity resolveMountReference(String username, boolean shared, String rawMountRef) {
        if (rawMountRef == null || rawMountRef.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid mount ref");
        }
        try {
            UUID mountId = UUID.fromString(rawMountRef);
            return mountRepository.findById(mountId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
        } catch (IllegalArgumentException ex) {
            if (shared) {
                String mountName = extractSharedMountName(rawMountRef);
                String ownerUsername = extractSharedOwnerUsername(rawMountRef);
                if (!ownerUsername.isBlank() && !"-".equals(ownerUsername)) {
                    UUID ownerId = userRepository.findByUsername(ownerUsername)
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"))
                            .getId();
                    return mountRepository.findByOwnerIdAndNameAndStateNot(ownerId, mountName, "soft_deleted")
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
                }
                return mountRepository.findByNameAndStateNot(mountName, "soft_deleted")
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
            }
            UUID userId = userRepository.findByUsername(username)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID, "user not found"))
                    .getId();
            return mountRepository.findByOwnerIdAndNameAndStateNot(userId, rawMountRef, "soft_deleted")
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
        }
    }

    private String extractSharedMountName(String rawMountRef) {
        int index = rawMountRef.lastIndexOf(SHARED_ALIAS_DELIMITER);
        if (index <= 0) {
            return rawMountRef;
        }
        return rawMountRef.substring(0, index);
    }

    private String extractSharedOwnerUsername(String rawMountRef) {
        int index = rawMountRef.lastIndexOf(SHARED_ALIAS_DELIMITER);
        if (index <= 0 || index + SHARED_ALIAS_DELIMITER.length() >= rawMountRef.length()) {
            return "";
        }
        return rawMountRef.substring(index + SHARED_ALIAS_DELIMITER.length());
    }

    private String toPermissionPath(boolean shared, String mountName, String relPath) {
        String base = shared ? "./shared/" + mountName : "./personal/" + mountName;
        return ".".equals(relPath) ? base : base + "/" + relPath;
    }

    /**
     * 解析结果：统一返回目标挂载、驱动相对路径与权限语境。
     */
    public record ResolveResult(MountEntity mount, String relPath, String virtualPath, boolean shared, boolean ownerOrAdmin) {
    }
}
