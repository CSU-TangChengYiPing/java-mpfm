package com.mpfm.backend.application.share.v5;

import com.mpfm.backend.application.user.PlatformRole;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.ShareRoleEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.ShareRolePolicyEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.v5.ShareLinkV5Entity;
import com.mpfm.backend.infrastructure.persistence.entity.share.v5.ShareRoleTemplatePrivilegeV5Entity;
import com.mpfm.backend.infrastructure.persistence.entity.share.v5.ShareRoleTemplateV5Entity;
import com.mpfm.backend.infrastructure.persistence.entity.share.v5.SharedMountAccessV5Entity;
import com.mpfm.backend.infrastructure.persistence.repository.MountRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.ShareRolePolicyRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.ShareRoleRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.v5.ShareLinkV5Repository;
import com.mpfm.backend.infrastructure.persistence.repository.share.v5.ShareAuthorizationV5SqlRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.v5.ShareRoleTemplatePrivilegeV5Repository;
import com.mpfm.backend.infrastructure.persistence.repository.share.v5.ShareRoleTemplateV5Repository;
import com.mpfm.backend.infrastructure.persistence.repository.share.v5.SharedMountAccessV5Repository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * v5 授权链接服务。
 * 规则：链接只用于授予角色；访问期权限按“有效角色并集”判定，不回查链接状态。
 */
@Service
public class ShareAuthorizationV5Service {
    private static final String STATE_ACTIVE = "active";
    private static final String STATE_REVOKED = "revoked";
    private static final String STATE_DISABLED = "disabled";
    private static final String STATE_DELETED = "deleted";
    private static final String ROLE_OWNER = "owner";
    private static final String ROLE_VISITOR = "visitor";
    private static final String ROLE_COLLABORATOR = "collaborator";

    private final UserRepository userRepository;
    private final MountRepository mountRepository;
    private final ShareRoleRepository shareRoleRepository;
    private final ShareRolePolicyRepository shareRolePolicyRepository;
    private final ShareLinkV5Repository shareLinkV5Repository;
    private final SharedMountAccessV5Repository sharedMountAccessV5Repository;
    private final ShareRoleTemplateV5Repository shareRoleTemplateV5Repository;
    private final ShareRoleTemplatePrivilegeV5Repository shareRoleTemplatePrivilegeV5Repository;
    private final ShareAuthorizationV5SqlRepository shareAuthorizationV5SqlRepository;

    public ShareAuthorizationV5Service(UserRepository userRepository,
                                       MountRepository mountRepository,
                                       ShareRoleRepository shareRoleRepository,
                                       ShareRolePolicyRepository shareRolePolicyRepository,
                                       ShareLinkV5Repository shareLinkV5Repository,
                                       SharedMountAccessV5Repository sharedMountAccessV5Repository,
                                       ShareRoleTemplateV5Repository shareRoleTemplateV5Repository,
                                       ShareRoleTemplatePrivilegeV5Repository shareRoleTemplatePrivilegeV5Repository,
                                       ShareAuthorizationV5SqlRepository shareAuthorizationV5SqlRepository) {
        this.userRepository = userRepository;
        this.mountRepository = mountRepository;
        this.shareRoleRepository = shareRoleRepository;
        this.shareRolePolicyRepository = shareRolePolicyRepository;
        this.shareLinkV5Repository = shareLinkV5Repository;
        this.sharedMountAccessV5Repository = sharedMountAccessV5Repository;
        this.shareRoleTemplateV5Repository = shareRoleTemplateV5Repository;
        this.shareRoleTemplatePrivilegeV5Repository = shareRoleTemplatePrivilegeV5Repository;
        this.shareAuthorizationV5SqlRepository = shareAuthorizationV5SqlRepository;
    }

    /** 共享链接结果，返回链接状态、次数和归属角色，供前端展示与审计追踪。 */
    public record ShareLinkResult(UUID linkId, UUID mountId, UUID roleId, String token, String state,
                                  String startAt, String expireAt, Integer maxUses, int usedCount, UUID createdByUserId) { }
    /** 解析共享链接后的角色结果，只保留后续权限计算需要的最小字段集。 */
    public record ResolveResult(UUID mountId, UUID roleId, String state) { }
    /** 当前用户在共享挂载下命中的角色信息，面向“我的共享角色”列表。 */
    public record MyRoleResult(UUID mountId, UUID roleId, String roleName, String roleState,
                               String roleStartAt, String roleExpireAt, String grantedAt) { }
    /** 我的共享角色摘要，额外带上挂载名称和挂载拥有者，便于列表聚合展示。 */
    public record MyRoleSummaryResult(UUID mountId, String mountName, String mountOwner, UUID roleId, String roleName, String roleState,
                                      String roleStartAt, String roleExpireAt, String grantedAt) { }
    /** 挂载维度已授权角色结果，供挂载所有者查看“授予了谁、授予了什么角色”。 */
    public record GrantedRoleResult(UUID mountId, UUID roleId, String roleName, String roleState,
                                    String roleStartAt, String roleExpireAt, String grantedAt,
                                    UUID granteeUserId, String granteeUsername) { }
    /** 有效权限结果，表达指定路径在若干共享角色叠加后的最终可见/读写能力。 */
    public record EffectivePermissionResult(String path, boolean canVisible, boolean canRead, boolean canWrite,
                                            List<UUID> roleIds, String decisionSource) { }
    /** 角色模板结果，描述模板默认权限与版本号，供并发更新校验使用。 */
    public record RoleTemplateResult(UUID templateId, UUID mountId, UUID roleId, String name, String state,
                                     boolean defaultVisible, boolean defaultRead, boolean defaultWrite, long version) { }
    /** 角色模板特权结果，描述模板内某个目标路径的覆盖权限与版本号。 */
    public record RoleTemplatePrivilegeResult(UUID privilegeId, UUID templateId, UUID mountId, String targetPath,
                                              boolean allowVisible, boolean allowRead, boolean allowWrite, long version) { }

    private record PermissionBits(boolean visible, boolean read, boolean write) { }

    @Transactional
    public ShareLinkResult createLink(String operator, UUID mountId, UUID roleId, OffsetDateTime startAt,
                                      OffsetDateTime expireAt, Integer maxUses,
                                      OffsetDateTime roleStartAt, OffsetDateTime roleExpireAt) {
        UserEntity op = requireUser(operator);
        MountEntity mount = requireOwnerOrAdminMount(op, mountId);
        ensureSystemTemplatesForMount(mount);
        ShareRoleEntity role = requireRole(roleId);
        if (!role.getMountId().equals(mount.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "role not match mount");
        }
        if (ROLE_OWNER.equalsIgnoreCase(role.getName())) {
            throw new BusinessException(ErrorCode.OWNER_IMMUTABLE, "owner can not be granted");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ShareLinkV5Entity link = new ShareLinkV5Entity();
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
        link.setCreatedAt(now);
        link.setUpdatedAt(now);
        shareLinkV5Repository.save(link);
        return toLinkResult(link);
    }

    @Transactional
    public RoleTemplateResult createRoleTemplate(String operator, UUID mountId, String name,
                                                 boolean defaultVisible, boolean defaultRead, boolean defaultWrite) {
        UserEntity op = requireUser(operator);
        MountEntity mount = requireOwnerOrAdminMount(op, mountId);
        ensureSystemTemplatesForMount(mount);
        String normalizedName = normalizeTemplateName(name);
        if (shareRoleTemplateV5Repository.findByMountIdAndName(mount.getId(), normalizedName).isPresent()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "template name duplicated");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ShareRoleEntity role = new ShareRoleEntity();
        role.setId(UUID.randomUUID());
        role.setMountId(mount.getId());
        role.setCreatorUserId(op.getId());
        role.setName(normalizedName);
        role.setSystem(false);
        role.setState(STATE_ACTIVE);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        shareRoleRepository.save(role);

        ShareRoleTemplateV5Entity template = new ShareRoleTemplateV5Entity();
        template.setId(UUID.randomUUID());
        template.setMountId(mount.getId());
        template.setRoleId(role.getId());
        template.setName(normalizedName);
        template.setState(STATE_ACTIVE);
        template.setDefaultVisible(defaultVisible);
        template.setDefaultRead(defaultRead);
        template.setDefaultWrite(defaultWrite);
        template.setVersion(1L);
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        shareRoleTemplateV5Repository.save(template);
        return toTemplateResult(template);
    }

    @Transactional
    public RoleTemplateResult deleteRoleTemplate(String operator, UUID templateId) {
        UserEntity op = requireUser(operator);
        ShareRoleTemplateV5Entity template = shareRoleTemplateV5Repository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "template not found"));
        requireOwnerOrAdminMount(op, template.getMountId());
        if (isSystemTemplateName(template.getName())) {
            throw new BusinessException(ErrorCode.OWNER_IMMUTABLE, "system template immutable");
        }
        template.setState(STATE_DELETED);
        template.setVersion(template.getVersion() + 1);
        template.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        shareRoleTemplateV5Repository.save(template);
        return toTemplateResult(template);
    }

    @Transactional
    public RoleTemplatePrivilegeResult upsertRoleTemplatePrivilege(String operator, UUID templateId, String targetPath,
                                                                   boolean allowVisible, boolean allowRead, boolean allowWrite) {
        UserEntity op = requireUser(operator);
        ShareRoleTemplateV5Entity template = shareRoleTemplateV5Repository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "template not found"));
        requireOwnerOrAdminMount(op, template.getMountId());
        String normalizedPath = normalizeTemplateRelativePath(targetPath, template.getMountId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ShareRoleTemplatePrivilegeV5Entity row = shareRoleTemplatePrivilegeV5Repository
                .findByTemplateIdAndTargetPath(templateId, normalizedPath)
                .orElseGet(() -> {
                    ShareRoleTemplatePrivilegeV5Entity created = new ShareRoleTemplatePrivilegeV5Entity();
                    created.setId(UUID.randomUUID());
                    created.setTemplateId(templateId);
                    created.setMountId(template.getMountId());
                    created.setTargetPath(normalizedPath);
                    created.setVersion(0L);
                    created.setCreatedAt(now);
                    return created;
                });
        row.setMountId(template.getMountId());
        row.setAllowVisible(allowVisible);
        row.setAllowRead(allowRead);
        row.setAllowWrite(allowWrite);
        row.setVersion(row.getVersion() + 1);
        row.setUpdatedAt(now);
        shareRoleTemplatePrivilegeV5Repository.save(row);
        return toPrivilegeResult(row);
    }

    /** 批量更新模板特权：一次提交多个路径，避免前端逐路径 N 次请求。 */
    @Transactional
    public List<RoleTemplatePrivilegeResult> upsertRoleTemplatePrivilegesBatch(String operator, UUID templateId, List<String> targetPaths,
                                                                               boolean allowVisible, boolean allowRead, boolean allowWrite) {
        if (targetPaths == null || targetPaths.isEmpty()) {
            return List.of();
        }
        List<RoleTemplatePrivilegeResult> out = new ArrayList<>();
        for (String targetPath : targetPaths) {
            out.add(upsertRoleTemplatePrivilege(operator, templateId, targetPath, allowVisible, allowRead, allowWrite));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<RoleTemplateResult> listRoleTemplates(String operator, UUID mountId) {
        UserEntity op = requireUser(operator);
        MountEntity mount = requireOwnerOrAdminMount(op, mountId);
        ensureSystemTemplatesForMount(mount);
        return shareRoleTemplateV5Repository.findByMountId(mountId).stream()
                .filter(i -> !STATE_DELETED.equalsIgnoreCase(i.getState()))
                .map(this::toTemplateResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleTemplatePrivilegeResult> listRoleTemplatePrivileges(String operator, UUID templateId) {
        UserEntity op = requireUser(operator);
        ShareRoleTemplateV5Entity template = shareRoleTemplateV5Repository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "template not found"));
        requireOwnerOrAdminMount(op, template.getMountId());
        return shareRoleTemplatePrivilegeV5Repository.findByTemplateId(templateId).stream()
                .map(this::toPrivilegeResult)
                .toList();
    }

    @Transactional
    public void deleteRoleTemplatePrivilege(String operator, UUID templateId, UUID privilegeId) {
        UserEntity op = requireUser(operator);
        ShareRoleTemplateV5Entity template = shareRoleTemplateV5Repository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "template not found"));
        requireOwnerMount(op, template.getMountId());
        ShareRoleTemplatePrivilegeV5Entity privilege = shareRoleTemplatePrivilegeV5Repository.findById(privilegeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "privilege not found"));
        if (!templateId.equals(privilege.getTemplateId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "privilege not match template");
        }
        shareRoleTemplatePrivilegeV5Repository.delete(privilege);
    }

    @Transactional
    public ResolveResult resolveLink(String operator, String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "token required");
        }
        UserEntity user = requireUser(operator);
        ShareLinkV5Entity link = shareLinkV5Repository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.LINK_INVALID, "invalid"));
        SharedMountAccessV5Entity existing = sharedMountAccessV5Repository
                .findByUserIdAndMountIdAndRoleId(user.getId(), link.getMountId(), link.getRoleId())
                .orElse(null);
        if (existing != null && existing.isActive() && !isAccessExpired(existing, OffsetDateTime.now(ZoneOffset.UTC))) {
            return new ResolveResult(link.getMountId(), link.getRoleId(), STATE_ACTIVE);
        }
        validateLink(link);
        ShareRoleEntity role = requireRole(link.getRoleId());
        validateRoleWindow(link, role);

        SharedMountAccessV5Entity access = existing == null ? createAccess(user, link) : existing;
        access.setActive(true);
        access.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        sharedMountAccessV5Repository.save(access);

        if (existing == null) {
            link.setUsedCount(link.getUsedCount() + 1);
            link.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            shareLinkV5Repository.save(link);
        }
        return new ResolveResult(link.getMountId(), link.getRoleId(), link.getState());
    }

    @Transactional
    public ShareLinkResult revokeLink(String operator, UUID linkId) {
        UserEntity op = requireUser(operator);
        ShareLinkV5Entity link = shareLinkV5Repository.findById(linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "link not found"));
        if (op.getPlatformRole() == PlatformRole.USER && !link.getCreatedByUserId().equals(op.getId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "not owner");
        }
        link.setState(STATE_REVOKED);
        link.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        shareLinkV5Repository.save(link);
        return toLinkResult(link);
    }

    @Transactional
    public void deleteLink(String operator, UUID linkId) {
        UserEntity op = requireUser(operator);
        ShareLinkV5Entity link = shareLinkV5Repository.findById(linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "link not found"));
        if (op.getPlatformRole() == PlatformRole.USER && !link.getCreatedByUserId().equals(op.getId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "not owner");
        }
        shareLinkV5Repository.delete(link);
    }

    @Transactional(readOnly = true)
    public List<ShareLinkResult> listLinks(String operator, UUID mountId) {
        UserEntity op = requireUser(operator);
        MountEntity mount = requireOwnerOrAdminMount(op, mountId);
        return shareLinkV5Repository.findAll().stream()
                .filter(i -> i.getMountId().equals(mount.getId()))
                .map(this::toLinkResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MyRoleResult> listMyRoles(String operator, UUID mountId) {
        UserEntity user = requireUser(operator);
        mountRepository.findById(mountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
        return sharedMountAccessV5Repository.findByUserIdAndMountId(user.getId(), mountId).stream()
                .filter(SharedMountAccessV5Entity::isActive)
                .map(access -> {
                    ShareRoleEntity role = requireRole(access.getRoleId());
                    return new MyRoleResult(access.getMountId(), access.getRoleId(), role.getName(), role.getState(),
                            toStringOrNull(access.getRoleStartAt()), toStringOrNull(access.getRoleExpireAt()), toStringOrNull(access.getGrantedAt()));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MyRoleSummaryResult> listMyRolesSummary(String operator) {
        UserEntity user = requireUser(operator);
        return sharedMountAccessV5Repository.findByUserIdAndActiveTrue(user.getId()).stream()
                .map(access -> {
                    ShareRoleEntity role = requireRole(access.getRoleId());
                    MountEntity mount = mountRepository.findById(access.getMountId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
                    UserEntity owner = userRepository.findById(mount.getOwnerId()).orElse(null);
                    String mountOwner = owner == null ? "" : owner.getUsername();
                    return new MyRoleSummaryResult(access.getMountId(), mount.getName(), mountOwner, access.getRoleId(), role.getName(), role.getState(),
                            toStringOrNull(access.getRoleStartAt()), toStringOrNull(access.getRoleExpireAt()), toStringOrNull(access.getGrantedAt()));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GrantedRoleResult> listGrantedRoles(String operator, UUID mountId) {
        UserEntity op = requireUser(operator);
        requireOwnerOrAdminMount(op, mountId);
        return sharedMountAccessV5Repository.findByMountIdAndActiveTrue(mountId).stream()
                .map(access -> {
                    ShareRoleEntity role = requireRole(access.getRoleId());
                    UserEntity grantee = userRepository.findById(access.getUserId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "grantee user not found"));
                    return new GrantedRoleResult(access.getMountId(), access.getRoleId(), role.getName(), role.getState(),
                            toStringOrNull(access.getRoleStartAt()), toStringOrNull(access.getRoleExpireAt()), toStringOrNull(access.getGrantedAt()),
                            grantee.getId(), grantee.getUsername());
                })
                .toList();
    }

    @Transactional
    public GrantedRoleResult updateGrantedRole(String operator, UUID mountId, UUID granteeUserId, UUID currentRoleId, UUID nextRoleId, OffsetDateTime roleExpireAt) {
        UserEntity op = requireUser(operator);
        requireOwnerOrAdminMount(op, mountId);
        if (roleExpireAt != null && !roleExpireAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "roleExpireAt must be in future");
        }
        SharedMountAccessV5Entity access = sharedMountAccessV5Repository
                .findByUserIdAndMountIdAndRoleId(granteeUserId, mountId, currentRoleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "granted role not found"));
        if (!access.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "granted role revoked");
        }
        ShareRoleEntity nextRole = requireRole(nextRoleId);
        if (!mountId.equals(nextRole.getMountId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "role not match mount");
        }
        access.setRoleId(nextRoleId);
        access.setRoleExpireAt(roleExpireAt);
        access.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        sharedMountAccessV5Repository.save(access);
        UserEntity grantee = userRepository.findById(granteeUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "grantee user not found"));
        return new GrantedRoleResult(access.getMountId(), access.getRoleId(), nextRole.getName(), nextRole.getState(),
                toStringOrNull(access.getRoleStartAt()), toStringOrNull(access.getRoleExpireAt()), toStringOrNull(access.getGrantedAt()),
                grantee.getId(), grantee.getUsername());
    }

    @Transactional
    public void revokeGrantedRole(String operator, UUID mountId, UUID granteeUserId, UUID roleId) {
        UserEntity op = requireUser(operator);
        requireOwnerOrAdminMount(op, mountId);
        SharedMountAccessV5Entity access = sharedMountAccessV5Repository
                .findByUserIdAndMountIdAndRoleId(granteeUserId, mountId, roleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "granted role not found"));
        if (!access.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "granted role revoked");
        }
        access.setActive(false);
        access.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        sharedMountAccessV5Repository.save(access);
    }

    @Transactional(readOnly = true)
    public EffectivePermissionResult effective(String operator, UUID mountId, String path) {
        UserEntity user = requireUser(operator);
        MountEntity mount = mountRepository.findById(mountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
        ensureSystemTemplatesForMount(mount);
        if (user.getPlatformRole() != PlatformRole.USER || mount.getOwnerId().equals(user.getId())) {
            return new EffectivePermissionResult(path, true, true, true, List.of(), "owner_admin");
        }
        String normalized = normalizeRequestPath(path, mount);
        List<SharedMountAccessV5Entity> accesses = sharedMountAccessV5Repository.findByUserIdAndMountId(user.getId(), mountId).stream()
                .filter(SharedMountAccessV5Entity::isActive)
                .toList();
        if (accesses.isEmpty()) {
            return new EffectivePermissionResult(path, false, false, false, List.of(), "no_access");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<UUID> roleIds = new ArrayList<>();
        for (SharedMountAccessV5Entity access : accesses) {
            ShareRoleEntity role = requireRole(access.getRoleId());
            if (isRoleUnavailable(role, now) || isAccessExpired(access, now)) {
                continue;
            }
            roleIds.add(role.getId());
        }
        if (roleIds.isEmpty()) {
            return new EffectivePermissionResult(path, false, false, false, List.of(), "no_access");
        }
        Map<UUID, ShareRoleTemplateV5Entity> templateByRole = shareRoleTemplateV5Repository.findByRoleIdIn(roleIds).stream()
                .filter(t -> STATE_ACTIVE.equalsIgnoreCase(t.getState()))
                .collect(HashMap::new, (m, v) -> m.put(v.getRoleId(), v), HashMap::putAll);
        if (templateByRole.size() == roleIds.size()) {
            Map<String, ShareAuthorizationV5SqlRepository.TemplateEffectiveRow> sqlComputed =
                    shareAuthorizationV5SqlRepository.computeRoleUnionByTemplatesBatch(roleIds, List.of(normalized));
            ShareAuthorizationV5SqlRepository.TemplateEffectiveRow row = sqlComputed.get(normalized);
            if (row != null) {
                return new EffectivePermissionResult(path, row.canVisible(), row.canRead(), row.canWrite(), roleIds, "role_union");
            }
        }
        Set<String> candidatePaths = new LinkedHashSet<>(collectAncestorPaths(normalized));
        Map<UUID, List<ShareRoleTemplatePrivilegeV5Entity>> privilegeByTemplate = loadPrivilegesForTemplatesAndPaths(
                templateByRole.values().stream().map(ShareRoleTemplateV5Entity::getId).toList(),
                candidatePaths);
        Map<UUID, List<ShareRolePolicyEntity>> legacyPolicyByRole = shareRolePolicyRepository.findByRoleIdIn(roleIds).stream()
                .collect(HashMap::new,
                        (m, v) -> m.computeIfAbsent(v.getRoleId(), k -> new ArrayList<>()).add(v),
                        HashMap::putAll);

        PermissionBits current = calculateNodePermission(normalized, roleIds, templateByRole, privilegeByTemplate, legacyPolicyByRole);
        String parent = parentPath(normalized);
        while (parent != null) {
            PermissionBits parentBits = calculateNodePermission(parent, roleIds, templateByRole, privilegeByTemplate, legacyPolicyByRole);
            current = new PermissionBits(
                    current.visible && parentBits.visible,
                    current.read && parentBits.read,
                    current.write && parentBits.write);
            parent = parentPath(parent);
        }
        return new EffectivePermissionResult(path, current.visible, current.read, current.write, roleIds, "role_union");
    }

    @Transactional(readOnly = true)
    public Map<String, EffectivePermissionResult> effectiveBatch(String operator, UUID mountId, List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return Map.of();
        }
        UserEntity user = requireUser(operator);
        MountEntity mount = mountRepository.findById(mountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
        ensureSystemTemplatesForMount(mount);
        Map<String, String> normalizedByInput = new LinkedHashMap<>();
        Set<String> candidatePaths = new LinkedHashSet<>();
        for (String path : paths) {
            String normalizedPath = normalizeRequestPath(path, mount);
            normalizedByInput.put(path, normalizedPath);
            candidatePaths.addAll(collectAncestorPaths(normalizedPath));
        }
        if (user.getPlatformRole() != PlatformRole.USER || mount.getOwnerId().equals(user.getId())) {
            Map<String, EffectivePermissionResult> adminResult = new LinkedHashMap<>();
            for (String path : paths) {
                adminResult.put(path, new EffectivePermissionResult(path, true, true, true, List.of(), "owner_admin"));
            }
            return adminResult;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<SharedMountAccessV5Entity> accesses = sharedMountAccessV5Repository.findByUserIdAndMountId(user.getId(), mountId).stream()
                .filter(SharedMountAccessV5Entity::isActive)
                .toList();
        List<UUID> roleIds = new ArrayList<>();
        for (SharedMountAccessV5Entity access : accesses) {
            ShareRoleEntity role = requireRole(access.getRoleId());
            if (isRoleUnavailable(role, now) || isAccessExpired(access, now)) {
                continue;
            }
            roleIds.add(role.getId());
        }
        if (roleIds.isEmpty()) {
            Map<String, EffectivePermissionResult> noAccess = new LinkedHashMap<>();
            for (String path : paths) {
                noAccess.put(path, new EffectivePermissionResult(path, false, false, false, List.of(), "no_access"));
            }
            return noAccess;
        }
        Map<UUID, ShareRoleTemplateV5Entity> templateByRole = shareRoleTemplateV5Repository.findByRoleIdIn(roleIds).stream()
                .filter(t -> STATE_ACTIVE.equalsIgnoreCase(t.getState()))
                .collect(HashMap::new, (m, v) -> m.put(v.getRoleId(), v), HashMap::putAll);
        if (templateByRole.size() == roleIds.size()) {
            Map<String, ShareAuthorizationV5SqlRepository.TemplateEffectiveRow> sqlComputed =
                    shareAuthorizationV5SqlRepository.computeRoleUnionByTemplatesBatch(roleIds, new ArrayList<>(normalizedByInput.values()));
            Map<String, EffectivePermissionResult> output = new LinkedHashMap<>();
            for (String rawPath : paths) {
                String normalized = normalizedByInput.get(rawPath);
                ShareAuthorizationV5SqlRepository.TemplateEffectiveRow row = sqlComputed.get(normalized);
                if (row == null) {
                    output.put(rawPath, new EffectivePermissionResult(rawPath, false, false, false, roleIds, "role_union"));
                    continue;
                }
                output.put(rawPath, new EffectivePermissionResult(rawPath, row.canVisible(), row.canRead(), row.canWrite(), roleIds, "role_union"));
            }
            return output;
        }
        Map<UUID, List<ShareRoleTemplatePrivilegeV5Entity>> privilegeByTemplate = loadPrivilegesForTemplatesAndPaths(
                templateByRole.values().stream().map(ShareRoleTemplateV5Entity::getId).toList(),
                candidatePaths);
        Map<UUID, List<ShareRolePolicyEntity>> legacyPolicyByRole = shareRolePolicyRepository.findByRoleIdIn(roleIds).stream()
                .collect(HashMap::new,
                        (m, v) -> m.computeIfAbsent(v.getRoleId(), k -> new ArrayList<>()).add(v),
                        HashMap::putAll);
        Map<String, EffectivePermissionResult> output = new LinkedHashMap<>();
        for (String rawPath : paths) {
            String normalized = normalizedByInput.get(rawPath);
            PermissionBits current = calculateNodePermission(normalized, roleIds, templateByRole, privilegeByTemplate, legacyPolicyByRole);
            String parent = parentPath(normalized);
            while (parent != null) {
                PermissionBits parentBits = calculateNodePermission(parent, roleIds, templateByRole, privilegeByTemplate, legacyPolicyByRole);
                current = new PermissionBits(
                        current.visible && parentBits.visible,
                        current.read && parentBits.read,
                        current.write && parentBits.write);
                parent = parentPath(parent);
            }
            output.put(rawPath, new EffectivePermissionResult(rawPath, current.visible, current.read, current.write, roleIds, "role_union"));
        }
        return output;
    }

    @Transactional(readOnly = true)
    public Map<String, EffectivePermissionResult> effectiveByTemplateBatch(String operator, UUID mountId, UUID templateId, List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return Map.of();
        }
        UserEntity op = requireUser(operator);
        MountEntity mount = requireOwnerMount(op, mountId);
        ensureSystemTemplatesForMount(mount);
        ShareRoleTemplateV5Entity template = shareRoleTemplateV5Repository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "template not found"));
        if (!mountId.equals(template.getMountId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "template not match mount");
        }
        Map<String, String> normalizedByInput = new LinkedHashMap<>();
        for (String rawPath : paths) {
            normalizedByInput.put(rawPath, normalizeTemplateRelativePath(rawPath, mountId));
        }
        Map<String, ShareAuthorizationV5SqlRepository.TemplateEffectiveRow> sqlComputed =
                shareAuthorizationV5SqlRepository.computeTemplateEffectiveBatch(templateId, new ArrayList<>(normalizedByInput.values()));
        Map<String, EffectivePermissionResult> output = new LinkedHashMap<>();
        for (String rawPath : paths) {
            String normalized = normalizedByInput.get(rawPath);
            ShareAuthorizationV5SqlRepository.TemplateEffectiveRow row = sqlComputed.get(normalized);
            if (row == null) {
                output.put(rawPath, new EffectivePermissionResult(
                        rawPath,
                        template.isDefaultVisible(),
                        template.isDefaultRead(),
                        template.isDefaultWrite(),
                        List.of(template.getRoleId()),
                        "template_preview"));
                continue;
            }
            output.put(rawPath, new EffectivePermissionResult(
                    rawPath,
                    row.canVisible(),
                    row.canRead(),
                    row.canWrite(),
                    List.of(template.getRoleId()),
                    "template_preview"));
        }
        return output;
    }

    private PermissionBits calculateNodePermission(String normalizedPath,
                                                   List<UUID> roleIds,
                                                   Map<UUID, ShareRoleTemplateV5Entity> templateByRole,
                                                   Map<UUID, List<ShareRoleTemplatePrivilegeV5Entity>> privilegeByTemplate,
                                                   Map<UUID, List<ShareRolePolicyEntity>> legacyPolicyByRole) {
        boolean visible = false;
        boolean read = false;
        boolean write = false;
        for (UUID roleId : roleIds) {
            ShareRoleTemplateV5Entity template = templateByRole.get(roleId);
            if (template == null) {
                ShareRolePolicyEntity legacy = resolveLegacyPolicy(legacyPolicyByRole.getOrDefault(roleId, List.of()), normalizedPath);
                if (legacy != null) {
                    visible = visible || legacy.isCanVisible();
                    read = read || legacy.isCanRead();
                    write = write || legacy.isCanWrite();
                }
                continue;
            }
            boolean roleVisible = template.isDefaultVisible();
            boolean roleRead = template.isDefaultRead();
            boolean roleWrite = template.isDefaultWrite();
            List<ShareRoleTemplatePrivilegeV5Entity> privileges = privilegeByTemplate.getOrDefault(template.getId(), List.of());
            for (ShareRoleTemplatePrivilegeV5Entity privilege : privileges) {
                if (!normalizedPath.equals(normalizeTemplateRelativePath(privilege.getTargetPath(), privilege.getMountId()))) {
                    continue;
                }
                roleVisible = privilege.isAllowVisible();
                roleRead = privilege.isAllowRead();
                roleWrite = privilege.isAllowWrite();
                break;
            }
            visible = visible || roleVisible;
            read = read || roleRead;
            write = write || roleWrite;
        }
        return new PermissionBits(visible, read, write);
    }

    private ShareRolePolicyEntity resolveLegacyPolicy(List<ShareRolePolicyEntity> policies, String normalizedPath) {
        ShareRolePolicyEntity best = null;
        int bestLen = -1;
        for (ShareRolePolicyEntity policy : policies) {
            String pattern = normalizePolicyPath(policy.getPathPattern());
            if (!normalizedPath.startsWith(pattern)) {
                continue;
            }
            if (pattern.length() > bestLen) {
                best = policy;
                bestLen = pattern.length();
            }
        }
        return best;
    }

    private PermissionBits calculateTemplateNodePermission(ShareRoleTemplateV5Entity template,
                                                           Map<String, ShareRoleTemplatePrivilegeV5Entity> privilegeByPath,
                                                           String normalizedPath) {
        boolean visible = template.isDefaultVisible();
        boolean read = template.isDefaultRead();
        boolean write = template.isDefaultWrite();
        ShareRoleTemplatePrivilegeV5Entity privilege = privilegeByPath.get(normalizedPath);
        if (privilege != null) {
            visible = privilege.isAllowVisible();
            read = privilege.isAllowRead();
            write = privilege.isAllowWrite();
        }
        return new PermissionBits(visible, read, write);
    }

    /** 数据库侧先按模板+候选路径过滤，减少内存侧无关特权扫描。 */
    private Map<UUID, List<ShareRoleTemplatePrivilegeV5Entity>> loadPrivilegesForTemplatesAndPaths(List<UUID> templateIds,
                                                                                                    Set<String> candidatePaths) {
        if (templateIds == null || templateIds.isEmpty() || candidatePaths == null || candidatePaths.isEmpty()) {
            return Map.of();
        }
        return shareRoleTemplatePrivilegeV5Repository.findByTemplateIdInAndTargetPathIn(templateIds, candidatePaths).stream()
                .collect(HashMap::new,
                        (m, v) -> m.computeIfAbsent(v.getTemplateId(), k -> new ArrayList<>()).add(v),
                        HashMap::putAll);
    }

    /** 收集当前路径与所有祖先路径，保持“当前节点与父链逐级 AND”语义。 */
    private List<String> collectAncestorPaths(String normalizedPath) {
        List<String> out = new ArrayList<>();
        String current = normalizedPath;
        while (current != null && !current.isBlank()) {
            out.add(current);
            current = parentPath(current);
        }
        return out;
    }

    private String parentPath(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return null;
        }
        int idx = normalizedPath.lastIndexOf('/');
        if (idx <= 1) {
            return null;
        }
        return normalizedPath.substring(0, idx);
    }

    private SharedMountAccessV5Entity createAccess(UserEntity user, ShareLinkV5Entity link) {
        SharedMountAccessV5Entity row = new SharedMountAccessV5Entity();
        row.setId(UUID.randomUUID());
        row.setUserId(user.getId());
        row.setMountId(link.getMountId());
        row.setRoleId(link.getRoleId());
        row.setActive(true);
        row.setGrantedByLinkId(link.getId());
        row.setGrantedAt(OffsetDateTime.now(ZoneOffset.UTC));
        row.setRoleStartAt(link.getRoleStartAt());
        row.setRoleExpireAt(link.getRoleExpireAt());
        row.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return row;
    }

    private void validateLink(ShareLinkV5Entity link) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
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

    private void validateRoleWindow(ShareLinkV5Entity link, ShareRoleEntity role) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (isRoleUnavailable(role, now)) {
            throw new BusinessException(ErrorCode.ROLE_EXPIRED, "expired");
        }
        if (link.getRoleStartAt() != null && now.isBefore(link.getRoleStartAt())) {
            throw new BusinessException(ErrorCode.ROLE_EXPIRED, "not started");
        }
        if (link.getRoleExpireAt() != null && now.isAfter(link.getRoleExpireAt())) {
            throw new BusinessException(ErrorCode.ROLE_EXPIRED, "expired");
        }
    }

    private boolean isRoleUnavailable(ShareRoleEntity role, OffsetDateTime now) {
        return "disabled".equals(role.getState()) || (role.getRoleExpiresAt() != null && now.isAfter(role.getRoleExpiresAt()));
    }

    private boolean isAccessExpired(SharedMountAccessV5Entity access, OffsetDateTime now) {
        if (access.getRoleStartAt() != null && now.isBefore(access.getRoleStartAt())) {
            return true;
        }
        return access.getRoleExpireAt() != null && now.isAfter(access.getRoleExpireAt());
    }

    private UserEntity requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID, "user not found"));
    }

    private ShareRoleEntity requireRole(UUID roleId) {
        return shareRoleRepository.findById(roleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "role not found"));
    }

    private MountEntity requireOwnerOrAdminMount(UserEntity operator, UUID mountId) {
        MountEntity mount = mountRepository.findById(mountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
        if (operator.getPlatformRole() != PlatformRole.USER || mount.getOwnerId().equals(operator.getId())) {
            return mount;
        }
        throw new BusinessException(ErrorCode.PERMISSION_DENIED, "mount denied");
    }

    private MountEntity requireOwnerMount(UserEntity operator, UUID mountId) {
        MountEntity mount = mountRepository.findById(mountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
        if (mount.getOwnerId().equals(operator.getId())) {
            return mount;
        }
        throw new BusinessException(ErrorCode.PERMISSION_DENIED, "mount denied");
    }

    private String normalizePolicyPath(String path) {
        if (path == null || path.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "path required");
        }
        String value = path.trim().replace('\\', '/');
        String normalized = normalizeByScopePrefix(value);
        if (normalized != null) {
            return normalized;
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        return value;
    }

    /**
     * 有效权限接口接收的是“请求视角路径”，这里先去掉 `/personal`、`/shared`
     * 及其后面的挂载标识，再交给策略归一化，避免把挂载名或挂载 ID 当成业务目录名。
     */
    private String normalizeRequestPath(String path, MountEntity mount) {
        if (path == null || path.isBlank() || ".".equals(path)) {
            return "/";
        }
        String normalized = path.trim().replace('\\', '/');
        normalized = stripNamespaceMount(normalized, "./personal/", mount);
        normalized = stripNamespaceMount(normalized, "./shared/", mount);
        normalized = stripNamespaceMount(normalized, "/personal/", mount);
        normalized = stripNamespaceMount(normalized, "/shared/", mount);
        return normalizePolicyPath(normalized);
    }

    /**
     * 共享命名空间下的有效权限查询会把路径拼成 `前缀/挂载标识/相对路径`。
     * 这里只剥离前缀和挂载标识，保留真正参与权限判断的相对目录。
     */
    private String stripNamespaceMount(String path, String prefix, MountEntity mount) {
        if (!path.startsWith(prefix)) {
            return path;
        }
        String remain = path.substring(prefix.length());
        if (remain.isBlank()) {
            return "/";
        }
        int nextSlash = remain.indexOf('/');
        if (nextSlash < 0) {
            return mountTokenMatch(remain, mount) ? "/" : remain;
        }
        String head = remain.substring(0, nextSlash);
        if (!mountTokenMatch(head, mount)) {
            return remain;
        }
        String stripped = remain.substring(nextSlash + 1);
        return stripped.isBlank() ? "/" : stripped;
    }

    private boolean mountTokenMatch(String value, MountEntity mount) {
        if (value == null || mount == null) {
            return false;
        }
        String lower = value.toLowerCase();
        return mount.getId().toString().equalsIgnoreCase(value)
                || mount.getName().equalsIgnoreCase(value)
                || lower.contains("---")
                || looksLikeUuid(value);
    }

    private boolean looksLikeUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    private String normalizeByScopePrefix(String value) {
        if ("./personal".equals(value) || "./shared".equals(value)) {
            return "/";
        }
        String personal = normalizePrefixedPath(value, "./personal/");
        if (personal != null) {
            return personal;
        }
        return normalizePrefixedPath(value, "./shared/");
    }

    private String normalizePrefixedPath(String value, String prefix) {
        if (!value.startsWith(prefix)) {
            return null;
        }
        String remain = value.substring(prefix.length());
        if (remain.isBlank()) {
            return "/";
        }
        if (!remain.startsWith("/")) {
            return "/" + remain;
        }
        return remain;
    }

    /**
     * 归一化模板特权路径：仅允许“挂载内相对路径”，禁止携带虚拟命名空间绝对前缀。
     */
    private String normalizeTemplateRelativePath(String path, UUID mountId) {
        if (path == null || path.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "targetPath required");
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        String mountToken = mountId == null ? "" : mountId.toString().toLowerCase();
        // if (!mountToken.isBlank()) {
        //     String lower = normalized.toLowerCase();
        //     String p1 = "/personal/" + mountToken;
        //     String p2 = "personal/" + mountToken;
        //     String p3 = "./personal/" + mountToken;
        //     if (lower.equals(p1) || lower.equals(p2) || lower.equals(p3)) {
        //         normalized = "/";
        //     } else if (lower.startsWith(p1 + "/")) {
        //         normalized = normalized.substring(p1.length());
        //     } else if (lower.startsWith(p2 + "/")) {
        //         normalized = normalized.substring(p2.length());
        //     } else if (lower.startsWith(p3 + "/")) {
        //         normalized = normalized.substring(p3.length());
        //     }
        // }
        if (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            return "/";
        }
        String[] parts = normalized.split("/");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "targetPath contains parent segment");
            }
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(part);
        }
        return out.length() == 0 ? "/" : out.toString();
    }

    private String normalizeTemplateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "template name required");
        }
        String normalized = name.trim();
        if (normalized.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "template name too long");
        }
        return normalized;
    }

    private ShareLinkResult toLinkResult(ShareLinkV5Entity link) {
        return new ShareLinkResult(link.getId(), link.getMountId(), link.getRoleId(), link.getToken(), link.getState(),
                toStringOrNull(link.getStartAt()), toStringOrNull(link.getExpireAt()), link.getMaxUses(), link.getUsedCount(), link.getCreatedByUserId());
    }

    private String toStringOrNull(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private RoleTemplateResult toTemplateResult(ShareRoleTemplateV5Entity template) {
        return new RoleTemplateResult(template.getId(), template.getMountId(), template.getRoleId(), template.getName(), template.getState(),
                template.isDefaultVisible(), template.isDefaultRead(), template.isDefaultWrite(), template.getVersion());
    }

    private RoleTemplatePrivilegeResult toPrivilegeResult(ShareRoleTemplatePrivilegeV5Entity privilege) {
        return new RoleTemplatePrivilegeResult(privilege.getId(), privilege.getTemplateId(), privilege.getMountId(), privilege.getTargetPath(),
                privilege.isAllowVisible(), privilege.isAllowRead(), privilege.isAllowWrite(), privilege.getVersion());
    }

    @Transactional
    public void ensureSystemTemplatesForMount(UUID mountId) {
        MountEntity mount = mountRepository.findById(mountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
        ensureSystemTemplatesForMount(mount);
    }

    private void ensureSystemTemplatesForMount(MountEntity mount) {
        ensureSystemTemplate(mount, ROLE_OWNER, true, true, true);
        ensureSystemTemplate(mount, ROLE_VISITOR, true, true, false);
        ensureSystemTemplate(mount, ROLE_COLLABORATOR, true, true, true);
    }

    private void ensureSystemTemplate(MountEntity mount, String roleName,
                                      boolean defaultVisible, boolean defaultRead, boolean defaultWrite) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ShareRoleEntity role = shareRoleRepository.findByMountIdAndName(mount.getId(), roleName).orElse(null);
        if (role == null) {
            role = new ShareRoleEntity();
            role.setId(UUID.randomUUID());
            role.setMountId(mount.getId());
            role.setCreatorUserId(mount.getOwnerId());
            role.setName(roleName);
            role.setSystem(true);
            role.setState(STATE_ACTIVE);
            role.setRoleExpiresAt(null);
            role.setCreatedAt(now);
            role.setUpdatedAt(now);
            shareRoleRepository.save(role);
        }
        ShareRoleTemplateV5Entity template = shareRoleTemplateV5Repository.findByRoleId(role.getId()).orElse(null);
        if (template == null) {
            template = new ShareRoleTemplateV5Entity();
            template.setId(UUID.randomUUID());
            template.setMountId(mount.getId());
            template.setRoleId(role.getId());
            template.setName(roleName);
            template.setState(STATE_ACTIVE);
            template.setDefaultVisible(defaultVisible);
            template.setDefaultRead(defaultRead);
            template.setDefaultWrite(defaultWrite);
            template.setVersion(1L);
            template.setCreatedAt(now);
            template.setUpdatedAt(now);
            shareRoleTemplateV5Repository.save(template);
            return;
        }
        boolean changed = false;
        if (!STATE_ACTIVE.equalsIgnoreCase(template.getState())) {
            template.setState(STATE_ACTIVE);
            changed = true;
        }
        if (template.isDefaultVisible() != defaultVisible) {
            template.setDefaultVisible(defaultVisible);
            changed = true;
        }
        if (template.isDefaultRead() != defaultRead) {
            template.setDefaultRead(defaultRead);
            changed = true;
        }
        if (template.isDefaultWrite() != defaultWrite) {
            template.setDefaultWrite(defaultWrite);
            changed = true;
        }
        if (changed) {
            template.setVersion(template.getVersion() + 1);
            template.setUpdatedAt(now);
            shareRoleTemplateV5Repository.save(template);
        }
    }

    private boolean isSystemTemplateName(String name) {
        if (name == null) {
            return false;
        }
        String normalized = name.trim().toLowerCase();
        return ROLE_OWNER.equals(normalized) || ROLE_VISITOR.equals(normalized) || ROLE_COLLABORATOR.equals(normalized);
    }
}
