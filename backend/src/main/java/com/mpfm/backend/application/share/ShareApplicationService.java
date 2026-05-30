package com.mpfm.backend.application.share;

import com.mpfm.backend.infrastructure.persistence.entity.share.ShareLinkEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.ShareRoleEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.ShareRolePolicyEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 共享应用服务，负责共享角色、路径策略、共享链接与有效权限计算流程编排。
 */
@Service
public class ShareApplicationService {

    private final ShareRoleService shareRoleService;
    private final ShareLinkService shareLinkService;
    private final SharePermissionService sharePermissionService;

    public ShareApplicationService(ShareRoleService shareRoleService,
                                   ShareLinkService shareLinkService,
                                   SharePermissionService sharePermissionService) {
        this.shareRoleService = shareRoleService;
        this.shareLinkService = shareLinkService;
        this.sharePermissionService = sharePermissionService;
    }

    /** 路径策略命令模型，承载策略更新时的路径模式与权限位参数。 */
    public record PathPolicyCommand(String pathPattern, boolean canVisible, boolean canRead, boolean canWrite) { }
    /** 共享角色结果模型，返回角色标识、状态与过期时间。 */
    public record ShareRoleResult(UUID roleId, UUID mountId, String name, String state, String roleExpiresAt) { }
    /** 共享链接结果模型，返回链接生命周期与使用次数信息。 */
    public record ShareLinkResult(UUID linkId, UUID mountId, UUID roleId, String token, String state,
                                  String startAt, String expireAt, int usedCount, Integer maxUses) { }
    /** 共享解析结果模型，返回解析后的挂载、角色与链接状态。 */
    public record ResolveResult(UUID mountId, UUID roleId, String state, String token) { }
    /** 路径策略结果模型，返回策略标识、路径模式与权限位。 */
    public record PathPolicyResult(UUID policyId, UUID roleId, String pathPattern, boolean canVisible, boolean canRead, boolean canWrite) { }
    /** 有效权限结果模型，表示指定路径最终计算后的权限集合。 */
    public record EffectivePermissionResult(String path, boolean canVisible, boolean canRead, boolean canWrite, UUID roleId) { }

    public ShareRoleResult createRole(String operator, UUID mountId, String name, OffsetDateTime roleExpiresAt) {
        return shareRoleService.createRole(operator, mountId, name, roleExpiresAt);
    }

    public List<ShareRoleResult> listRoles(String operator, UUID mountId) {
        return shareRoleService.listRoles(operator, mountId);
    }

    public ShareRoleResult updateRole(String operator, UUID roleId, String name, OffsetDateTime roleExpiresAt) {
        return shareRoleService.updateRole(operator, roleId, name, roleExpiresAt);
    }

    public ShareRoleResult disableRole(String operator, UUID roleId) {
        return shareRoleService.disableRole(operator, roleId);
    }

    public void deleteRole(String operator, UUID roleId) {
        shareRoleService.deleteRole(operator, roleId);
    }

    public List<PathPolicyResult> updatePolicies(String operator, UUID roleId, List<PathPolicyCommand> policies) {
        return shareRoleService.updatePolicies(operator, roleId, policies);
    }

    public ShareLinkResult createLink(String operator, UUID mountId, UUID roleId, OffsetDateTime startAt, OffsetDateTime expireAt,
                                      Integer maxUses, OffsetDateTime roleStartAt, OffsetDateTime roleExpireAt) {
        return shareLinkService.createLink(operator, mountId, roleId, startAt, expireAt, maxUses, roleStartAt, roleExpireAt);
    }

    public List<ShareLinkResult> listLinks(String operator) {
        return shareLinkService.listLinks(operator);
    }

    public ShareLinkResult getLink(String operator, UUID linkId) {
        return shareLinkService.getLink(operator, linkId);
    }

    public ShareLinkResult updateLink(String operator, UUID linkId, OffsetDateTime startAt, OffsetDateTime expireAt, Integer maxUses) {
        return shareLinkService.updateLink(operator, linkId, startAt, expireAt, maxUses);
    }

    public ShareLinkResult revokeLink(String operator, UUID linkId) {
        return shareLinkService.revokeLink(operator, linkId);
    }

    public void deleteLink(String operator, UUID linkId) {
        shareLinkService.deleteLink(operator, linkId);
    }

    public ResolveResult resolveLink(String operator, String token) {
        return shareLinkService.resolveLink(operator, token);
    }

    public ResolveResult switchRole(String operator, UUID mountId, UUID roleId) {
        return shareLinkService.switchRole(operator, mountId, roleId);
    }

    public EffectivePermissionResult effective(String operator, UUID mountId, String path) {
        return sharePermissionService.effective(operator, mountId, path);
    }

    public Map<String, EffectivePermissionResult> effectiveBatch(String operator, UUID mountId, List<String> paths) {
        return sharePermissionService.effectiveBatch(operator, mountId, paths);
    }

    static ShareRoleResult toRole(ShareRoleEntity role) {
        return new ShareRoleResult(role.getId(), role.getMountId(), role.getName(), role.getState(),
                role.getRoleExpiresAt() == null ? null : role.getRoleExpiresAt().toString());
    }

    static ShareLinkResult toLink(ShareLinkEntity link) {
        return new ShareLinkResult(link.getId(), link.getMountId(), link.getRoleId(), link.getToken(), link.getState(),
                link.getStartAt() == null ? null : link.getStartAt().toString(),
                link.getExpireAt() == null ? null : link.getExpireAt().toString(), link.getUsedCount(), link.getMaxUses());
    }

    static PathPolicyResult toPolicy(ShareRolePolicyEntity policy) {
        return new PathPolicyResult(policy.getId(), policy.getRoleId(), policy.getPathPattern(),
                policy.isCanVisible(), policy.isCanRead(), policy.isCanWrite());
    }
}




