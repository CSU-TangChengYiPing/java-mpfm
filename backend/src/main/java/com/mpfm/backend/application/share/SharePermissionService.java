package com.mpfm.backend.application.share;

import com.mpfm.backend.application.user.PlatformRole;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.ShareRolePolicyEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.SharedMountAccessEntity;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 共享权限聚合服务，负责共享审计、授权规则维护与权限预览/生效计算。
 */
@Component
public class SharePermissionService {
    private static final String REQUEST_CACHE_KEY = SharePermissionService.class.getName() + ".effectiveContextCache";
    private final ShareRepositories repositories;
    private final ShareAccessSupport support;
    private final ShareRoleService roleService;
    private final SharePathPolicySupport pathPolicySupport;

    SharePermissionService(ShareRepositories repositories,
                           ShareAccessSupport support,
                           ShareRoleService roleService,
                           SharePathPolicySupport pathPolicySupport) {
        this.repositories = repositories;
        this.support = support;
        this.roleService = roleService;
        this.pathPolicySupport = pathPolicySupport;
    }

    /**
     * 共享审计视图，面向前端展示共享相关操作轨迹。
     */
    public record ShareAuditView(UUID id, UUID mountId, String action, String actor, String result, String detail, String occurredAt) { }
    /**
     * 共享授权规则视图，表达“角色-路径范围-权限集合”的当前配置。
     */
    public record ShareGrantView(UUID id, UUID mountId, String role, List<String> pathScopes, List<String> permissions,
                                 String createdBy, String createdAt, String updatedAt) { }
    /**
     * 共享权限预览节点，表示指定路径在目标角色下的可见/读写能力。
     */
    public record SharePreviewNodeView(String path, boolean isDir, List<String> permissions, boolean visible, int depth) { }

    public List<ShareAuditView> listAudits(String operator, UUID mountId) {
        var op = support.requireUser(operator);
        support.requireOwnedOrAdminMount(op, mountId);
        return repositories.auditLogRepository.findAll().stream()
                .filter(row -> row.getAction() != null && row.getAction().startsWith("share_"))
                .limit(200)
                .map(row -> new ShareAuditView(
                        row.getId(),
                        mountId,
                        row.getAction(),
                        row.getOperator(),
                        row.getResult(),
                        row.getErrorCode(),
                        row.getCreatedAt() == null ? null : row.getCreatedAt().toString()))
                .toList();
    }

    public List<ShareGrantView> listGrants(String operator, UUID mountId) {
        var op = support.requireUser(operator);
        MountEntity mount = support.requireOwnedOrAdminMount(op, mountId);
        roleService.ensureBuiltInRoles(mount);
        List<ShareGrantView> out = new ArrayList<>();
        for (var role : repositories.shareRoleRepository.findByMountId(mountId)) {
            for (var policy : repositories.shareRolePolicyRepository.findByRoleId(role.getId())) {
                out.add(toGrant(policy, mountId, role.getName()));
            }
        }
        return out;
    }

    public ShareGrantView upsertGrant(String operator,
                                                         UUID mountId,
                                                         String roleName,
                                                         List<String> pathScopes,
                                                         List<String> permissions) {
        var op = support.requireUser(operator);
        MountEntity mount = support.requireOwnedOrAdminMount(op, mountId);
        roleService.ensureBuiltInRoles(mount);
        var role = repositories.shareRoleRepository.findByMountIdAndName(mountId, roleName)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "role not found in mount"));
        boolean canVisible = permissions.contains("visible");
        boolean canRead = permissions.contains("read") || permissions.contains("download");
        boolean canWrite = permissions.contains("write") || permissions.contains("delete") || permissions.contains("move");
        if (canWrite && !canRead) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "write requires read");
        }
        if (pathScopes == null || pathScopes.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "path scopes required");
        }
        ShareRolePolicyEntity saved = null;
        for (String scope : pathScopes) {
            String normalized = pathPolicySupport.normalizePolicyPath(scope);
            ShareRolePolicyEntity match = repositories.shareRolePolicyRepository.findByRoleId(role.getId()).stream()
                    .filter(x -> pathPolicySupport.normalizePolicyPath(x.getPathPattern()).equals(normalized))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                match = new ShareRolePolicyEntity();
                match.setId(UUID.randomUUID());
                match.setRoleId(role.getId());
                match.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            }
            match.setPathPattern(normalized);
            match.setCanVisible(canVisible);
            match.setCanRead(canRead);
            match.setCanWrite(canWrite);
            match.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            saved = repositories.shareRolePolicyRepository.save(match);
        }
        return toGrant(saved, mountId, role.getName());
    }

    public void deleteGrant(String operator, UUID mountId, UUID grantId) {
        var op = support.requireUser(operator);
        support.requireOwnedOrAdminMount(op, mountId);
        ShareRolePolicyEntity policy = repositories.shareRolePolicyRepository.findById(grantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "grant not found"));
        var role = support.requireRole(policy.getRoleId());
        if (!role.getMountId().equals(mountId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "grant not under mount");
        }
        repositories.shareRolePolicyRepository.delete(policy);
    }

    public List<SharePreviewNodeView> preview(String operator,
                                                                 UUID mountId,
                                                                 String roleName,
                                                                 String path,
                                                                 Integer maxDepth) {
        var op = support.requireUser(operator);
        MountEntity mount = support.requireOwnedOrAdminMount(op, mountId);
        roleService.ensureBuiltInRoles(mount);
        var role = repositories.shareRoleRepository.findByMountIdAndName(mountId, roleName)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "role not found in mount"));
        String normalized = pathPolicySupport.normalizePolicyPath(path);
        boolean canVisible = false;
        boolean canRead = false;
        boolean canWrite = false;
        for (var policy : repositories.shareRolePolicyRepository.findByRoleId(role.getId())) {
            String p = pathPolicySupport.normalizePolicyPath(policy.getPathPattern());
            if (normalized.startsWith(p)) {
                canVisible = policy.isCanVisible();
                canRead = policy.isCanRead();
                canWrite = policy.isCanWrite();
            }
        }
        List<String> perms = new ArrayList<>();
        if (canVisible) {
            perms.add("visible");
        }
        if (canRead) {
            perms.add("read");
            perms.add("download");
        }
        if (canWrite) {
            perms.add("write");
            perms.add("delete");
            perms.add("move");
        }
        int depth = maxDepth == null ? 1 : Math.max(1, maxDepth);
        return List.of(new SharePreviewNodeView(normalized, true, perms, canVisible, depth));
    }

    ShareApplicationService.EffectivePermissionResult effective(String operator, UUID mountId, String path) {
        PermissionEvalContext context = resolveContext(operator, mountId);
        return evaluatePath(path, context);
    }

    Map<String, ShareApplicationService.EffectivePermissionResult> effectiveBatch(String operator, UUID mountId, List<String> paths) {
        PermissionEvalContext context = resolveContext(operator, mountId);
        Map<String, ShareApplicationService.EffectivePermissionResult> out = new HashMap<>();
        for (String path : paths) {
            out.put(path, evaluatePath(path, context));
        }
        return out;
    }

    private PermissionEvalContext resolveContext(String operator, UUID mountId) {
        String cacheKey = operator + "|" + mountId;
        Map<String, PermissionEvalContext> cache = requestCache();
        if (cache != null && cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        var user = support.requireUser(operator);
        MountEntity mount = repositories.mountRepository.findById(mountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
        roleService.ensureBuiltInRoles(mount);

        PermissionEvalContext context;
        if (user.getPlatformRole() != PlatformRole.USER || mount.getOwnerId().equals(user.getId())) {
            context = new PermissionEvalContext(null, List.of(), true, false, false);
        } else {
            SharedMountAccessEntity active = repositories.sharedMountAccessRepository.findByUserIdAndMountId(user.getId(), mountId).stream()
                    .filter(SharedMountAccessEntity::isActive)
                    .findFirst().orElse(null);
            if (active == null) {
                context = new PermissionEvalContext(null, List.of(), false, true, false);
            } else {
                var role = support.requireRole(active.getRoleId());
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                if (support.isRoleUnavailable(role, now)) {
                    context = new PermissionEvalContext(role.getId(), List.of(), false, true, true);
                } else {
                    context = new PermissionEvalContext(role.getId(), repositories.shareRolePolicyRepository.findByRoleId(active.getRoleId()),
                            false, false, false);
                }
            }
        }
        if (cache != null) {
            cache.put(cacheKey, context);
        }
        return context;
    }

    private ShareApplicationService.EffectivePermissionResult evaluatePath(String path, PermissionEvalContext context) {
        if (context.ownerOrAdmin) {
            return new ShareApplicationService.EffectivePermissionResult(path, true, true, true, null);
        }
        if (context.noAccess) {
            return new ShareApplicationService.EffectivePermissionResult(path, false, false, false, context.roleId);
        }
        if (context.roleUnavailable) {
            return new ShareApplicationService.EffectivePermissionResult(path, false, false, false, context.roleId);
        }
        String normalizedPath = normalizeRequestPath(path);
        var policy = context.policies.stream()
                .filter(p -> normalizedPath.startsWith(pathPolicySupport.normalizePolicyPath(p.getPathPattern())))
                .max(Comparator.comparingInt(p -> pathPolicySupport.normalizePolicyPath(p.getPathPattern()).length()))
                .orElse(null);
        if (policy == null) {
            return new ShareApplicationService.EffectivePermissionResult(path, false, false, false, context.roleId);
        }
        return new ShareApplicationService.EffectivePermissionResult(path, policy.isCanVisible(), policy.isCanRead(), policy.isCanWrite(), context.roleId);
    }

    /**
     * 共享权限判断使用“请求视角路径”，如果带有 `/shared/<mountRef>/...` 之类的虚拟前缀，
     * 先把命名空间和挂载标识剥离，避免把同一份文件和策略按不同前缀比较。
     */
    private String normalizeRequestPath(String path) {
        if (path == null || path.isBlank() || ".".equals(path)) {
            return "/";
        }
        String normalized = path.trim().replace('\\', '/');
        normalized = stripNamespaceMount(normalized, "./personal/");
        normalized = stripNamespaceMount(normalized, "./shared/");
        normalized = stripNamespaceMount(normalized, "/personal/");
        normalized = stripNamespaceMount(normalized, "/shared/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 共享有效权限接口的入参会携带挂载命名空间前缀，这里统一去掉“前缀 + 挂载标识”两层，
     * 保证后续与策略表里的相对路径按同一基线比较。
     */
    private String stripNamespaceMount(String path, String prefix) {
        if (!path.startsWith(prefix)) {
            return path;
        }
        String remain = path.substring(prefix.length());
        if (remain.isBlank()) {
            return "/";
        }
        int nextSlash = remain.indexOf('/');
        if (nextSlash < 0) {
            return "/";
        }
        String stripped = remain.substring(nextSlash + 1);
        return stripped.isBlank() ? "/" : stripped;
    }

    @SuppressWarnings("unchecked")
    private Map<String, PermissionEvalContext> requestCache() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        Object existing = request.getAttribute(REQUEST_CACHE_KEY);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, PermissionEvalContext>) map;
        }
        Map<String, PermissionEvalContext> created = new HashMap<>();
        request.setAttribute(REQUEST_CACHE_KEY, created);
        return created;
    }

    private record PermissionEvalContext(UUID roleId,
                                         List<ShareRolePolicyEntity> policies,
                                         boolean ownerOrAdmin,
                                         boolean noAccess,
                                         boolean roleUnavailable) {
    }

    private ShareGrantView toGrant(ShareRolePolicyEntity policy, UUID mountId, String roleName) {
        List<String> permissions = new ArrayList<>();
        if (policy.isCanVisible()) {
            permissions.add("visible");
        }
        if (policy.isCanRead()) {
            permissions.add("read");
            permissions.add("download");
        }
        if (policy.isCanWrite()) {
            permissions.add("write");
            permissions.add("delete");
            permissions.add("move");
        }
        return new ShareGrantView(
                policy.getId(),
                mountId,
                roleName,
                List.of(policy.getPathPattern()),
                permissions,
                null,
                policy.getCreatedAt() == null ? null : policy.getCreatedAt().toString(),
                policy.getUpdatedAt() == null ? null : policy.getUpdatedAt().toString());
    }
}


