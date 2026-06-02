package com.mpfm.backend.application.file;

import com.mpfm.backend.application.share.v5.ShareAuthorizationV5Service;
import com.mpfm.backend.application.user.PlatformRole;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.repository.MountRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 文件访问校验服务，负责把挂载与共享权限统一收敛为读写可达性判断。
 */
@Component
public class FileAccessService {
    private static final String MESSAGE_PATH_NOT_FOUND = "path not found";
    private static final String CURRENT_DIR = ".";
    private static final String ROOT_PATH = "/";
    private static final String PERSONAL_PREFIX = "./personal/";
    private static final String SHARED_PREFIX = "./shared/";
    private static final String MESSAGE_RELATIVE_PATH_REQUIRED = "path must be relative under mount";
    private static final String LOCAL_TYPE = "local";

    private final ShareAuthorizationV5Service shareAuthorizationV5Service;
    private final MountRepository mountRepository;
    private final UserRepository userRepository;

    public FileAccessService(ShareAuthorizationV5Service shareAuthorizationV5Service,
                             MountRepository mountRepository,
                             UserRepository userRepository) {
        this.shareAuthorizationV5Service = shareAuthorizationV5Service;
        this.mountRepository = mountRepository;
        this.userRepository = userRepository;
    }

    // 校验文件访问权限
    public AccessContext requireAccess(String username, UUID mountId, String rawPath, boolean requireWrite, boolean requireRead) {
        MountEntity mount = mountRepository.findById(mountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
        UserEntity operator = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID, "user not found"));
        if (!"enabled".equals(mount.getState())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "mount disabled");
        }
        PathResolveResult resolved = resolveUnderMount(mount, rawPath);
        ShareAuthorizationV5Service.EffectivePermissionResult permission =
                shareAuthorizationV5Service.effective(username, mountId, resolved.virtualPath());
        boolean sharedContext = permission.roleIds() != null && !permission.roleIds().isEmpty();
        boolean ownerOrAdmin = isOwnerOrAdmin(operator, mount);
        if (!ownerOrAdmin) {
            if (!permission.canVisible()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, MESSAGE_PATH_NOT_FOUND);
            }
            if (requireRead && !permission.canRead()) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, "path not readable");
            }
            if (requireWrite && (!permission.canRead() || !permission.canWrite())) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, "path not writable");
            }
        }
        return new AccessContext(mount, resolved.physicalPath(), sharedContext, ownerOrAdmin, resolved.virtualPath());
    }

    private boolean isOwnerOrAdmin(UserEntity operator, MountEntity mount) {
        if (operator.getId() != null && operator.getId().equals(mount.getOwnerId())) {
            return true;
        }
        PlatformRole role = operator.getPlatformRole();
        return role != null && role != PlatformRole.USER;
    }

    public String toVirtualPath(MountEntity mount, Path physical, boolean shared) {
        String rel = toRelativePath(mount, physical.toString());
        String base = shared ? SHARED_PREFIX + mount.getName() : PERSONAL_PREFIX + mount.getName();
        return base + (rel.isBlank() || ".".equals(rel) ? "" : "/" + rel);
    }

    public String toVirtualPath(MountEntity mount, String driverPath, boolean shared) {
        String rel = toRelativePath(mount, driverPath);
        String base = shared ? SHARED_PREFIX + mount.getName() : PERSONAL_PREFIX + mount.getName();
        return base + (rel.isBlank() || ".".equals(rel) ? "" : "/" + rel);
    }

    public String toRelativePath(MountEntity mount, Path physical) {
        return toRelativePath(mount, physical.toString());
    }

    public String toRelativePath(MountEntity mount, String physicalPath) {
        if (isLocalMount(mount)) {
            Path physical = Path.of(physicalPath);
            String rel = Path.of(mount.getPhysicalRoot()).relativize(physical).toString().replace('\\', '/');
            return rel.isBlank() ? "." : rel;
        }
        String normalized = normalizeRelativePath(physicalPath.replace('\\', '/'));
        if (".".equals(normalized)) {
            return ".";
        }
        String base = remoteBasePath(mount);
        if (".".equals(base)) {
            return normalized;
        }
        String baseWithSlash = base.endsWith("/") ? base : base + "/";
        if (normalized.equals(base)) {
            return ".";
        }
        if (normalized.startsWith(baseWithSlash)) {
            return normalized.substring(baseWithSlash.length());
        }
        return normalized;
    }

    private PathResolveResult resolveUnderMount(MountEntity mount, String rawPath) {
        String path = normalizeRequestPath(rawPath);
        validateRequestPath(path);
        boolean shared = path.startsWith("/shared/") || path.startsWith("./shared/");
        String normalized = normalizeRelativePath(path);
        if (path.startsWith("/personal/")) {
            normalized = normalizeRelativePath(path.substring("/personal/".length()));
        } else if (path.startsWith("/shared/")) {
            normalized = normalizeRelativePath(path.substring("/shared/".length()));
        } else if (path.startsWith("./personal/")) {
            normalized = normalizeRelativePath(path.substring("./personal/".length()));
        } else if (path.startsWith("./shared/")) {
            normalized = normalizeRelativePath(path.substring("./shared/".length()));
        }
        if (normalized.isBlank()) {
            normalized = CURRENT_DIR;
        }
        if (!isLocalMount(mount)) {
            Path logicalPath = ".".equals(normalized) ? Path.of(".") : Path.of(normalized).normalize();
            return new PathResolveResult(logicalPath, shared, toVirtualPath(mount, logicalPath, shared));
        }
        Path root = Path.of(mount.getPhysicalRoot()).normalize();
        Path resolved = ".".equals(normalized) ? root : root.resolve(normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid path");
        }
        return new PathResolveResult(resolved, shared, toVirtualPath(mount, resolved, shared));
    }

    private String normalizeRequestPath(String rawPath) {
        String path = rawPath == null || rawPath.isBlank() ? CURRENT_DIR : rawPath.trim();
        if (ROOT_PATH.equals(path)) {
            return CURRENT_DIR;
        }
        return path.replace('\\', '/');
    }

    private void validateRequestPath(String path) {
        if (path.contains("..")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid path");
        }
        if (path.startsWith(PERSONAL_PREFIX)
                || path.startsWith(SHARED_PREFIX)
                || "./personal".equals(path)
                || "./shared".equals(path)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, MESSAGE_RELATIVE_PATH_REQUIRED);
        }
    }

    private String normalizeRelativePath(String path) {
        if (CURRENT_DIR.equals(path)) {
            return CURRENT_DIR;
        }
        String relative = path.startsWith("/") ? path.substring(1) : path;
        if (relative.isBlank()) {
            return CURRENT_DIR;
        }
        return relative;
    }

    private boolean isLocalMount(MountEntity mount) {
        return LOCAL_TYPE.equalsIgnoreCase(mount.getType());
    }

    private String remoteBasePath(MountEntity mount) {
        try {
            URI uri = URI.create(mount.getPhysicalRoot());
            String rawPath = uri.getPath();
            if (rawPath == null || rawPath.isBlank()) {
                return ".";
            }
            return normalizeRelativePath(rawPath.replace('\\', '/'));
        } catch (Exception ex) {
            return ".";
        }
    }

    record PathResolveResult(Path physicalPath, boolean shared, String virtualPath) { }

    /**
     * 文件访问上下文，描述挂载、目标物理路径与权限来源，供上层业务复用。
     */
    public record AccessContext(MountEntity mount, Path target, boolean shared, boolean ownerOrAdmin, String virtualPath) { }
}


