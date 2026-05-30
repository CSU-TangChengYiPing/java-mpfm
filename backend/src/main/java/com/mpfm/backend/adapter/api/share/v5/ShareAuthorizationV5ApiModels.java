package com.mpfm.backend.adapter.api.share.v5;

import com.mpfm.backend.application.share.v5.ShareAuthorizationV5Service;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * v5 授权链接接口模型，定义请求与响应结构。
 */
public class ShareAuthorizationV5ApiModels {
    /** 创建链接请求。 */
    public record CreateLinkRequest(UUID roleId, OffsetDateTime startAt, OffsetDateTime expireAt, Integer maxUses,
                                    OffsetDateTime roleStartAt, OffsetDateTime roleExpireAt) { }
    /** 解析链接请求。 */
    public record ResolveLinkRequest(String token) { }
    /** 创建角色模板请求。 */
    public record CreateRoleTemplateRequest(String name, boolean defaultVisible, boolean defaultRead, boolean defaultWrite) { }
    /** 特权更新请求。 */
    public record UpsertRoleTemplatePrivilegeRequest(String targetPath, boolean allowVisible, boolean allowRead, boolean allowWrite) { }
    /** 特权批量更新请求。 */
    public record UpsertRoleTemplatePrivilegeBatchRequest(List<String> targetPaths, boolean allowVisible, boolean allowRead, boolean allowWrite) { }
    /** 模板代入批量求权请求。 */
    public record TemplateEffectiveBatchRequest(String templateId, List<String> paths) { }
    /** 更新已授权角色请求。 */
    public record UpdateGrantedRoleRequest(String roleId, OffsetDateTime roleExpireAt) { }
    /** 链接响应。 */
    public record LinkResponse(String linkId, String mountId, String roleId, String token, String state,
                               String startAt, String expireAt, Integer maxUses, int usedCount, String createdByUserId) {
        public static LinkResponse from(ShareAuthorizationV5Service.ShareLinkResult r) {
            return new LinkResponse(r.linkId().toString(), r.mountId().toString(), r.roleId().toString(), r.token(), r.state(),
                    r.startAt(), r.expireAt(), r.maxUses(), r.usedCount(), r.createdByUserId() == null ? null : r.createdByUserId().toString());
        }
    }
    /** 解析结果响应。 */
    public record ResolveResponse(String mountId, String roleId, String state) {
        public static ResolveResponse from(ShareAuthorizationV5Service.ResolveResult r) {
            return new ResolveResponse(r.mountId().toString(), r.roleId().toString(), r.state());
        }
    }
    /** 有效权限响应。 */
    public record PermissionResponse(String path, boolean canVisible, boolean canRead, boolean canWrite,
                                     List<String> roleIds, String decisionSource) {
        public static PermissionResponse from(ShareAuthorizationV5Service.EffectivePermissionResult r) {
            return new PermissionResponse(r.path(), r.canVisible(), r.canRead(), r.canWrite(),
                    r.roleIds().stream().map(UUID::toString).toList(), r.decisionSource());
        }
    }
    /** 我的角色响应。 */
    public record MyRoleResponse(String mountId, String roleId, String roleName, String roleState,
                                 String roleStartAt, String roleExpireAt, String grantedAt) {
        public static MyRoleResponse from(ShareAuthorizationV5Service.MyRoleResult r) {
            return new MyRoleResponse(r.mountId().toString(), r.roleId().toString(), r.roleName(), r.roleState(),
                    r.roleStartAt(), r.roleExpireAt(), r.grantedAt());
        }
    }

    /** 我的角色总览响应。 */
    public record MyRoleSummaryResponse(String mountId, String mountName, String mountOwner, String roleId, String roleName, String roleState,
                                        String roleStartAt, String roleExpireAt, String grantedAt) {
        public static MyRoleSummaryResponse from(ShareAuthorizationV5Service.MyRoleSummaryResult r) {
            return new MyRoleSummaryResponse(r.mountId().toString(), r.mountName(), r.mountOwner(), r.roleId().toString(), r.roleName(), r.roleState(),
                    r.roleStartAt(), r.roleExpireAt(), r.grantedAt());
        }
    }
    /** 挂载维度已授权角色响应。 */
    public record GrantedRoleResponse(String mountId, String roleId, String roleName, String roleState,
                                      String roleStartAt, String roleExpireAt, String grantedAt,
                                      String granteeUserId, String granteeUsername) {
        public static GrantedRoleResponse from(ShareAuthorizationV5Service.GrantedRoleResult r) {
            return new GrantedRoleResponse(
                    r.mountId().toString(), r.roleId().toString(), r.roleName(), r.roleState(),
                    r.roleStartAt(), r.roleExpireAt(), r.grantedAt(),
                    r.granteeUserId().toString(), r.granteeUsername());
        }
    }
    /** 角色模板响应。 */
    public record RoleTemplateResponse(String templateId, String mountId, String roleId, String name, String state,
                                       boolean defaultVisible, boolean defaultRead, boolean defaultWrite, long version) {
        public static RoleTemplateResponse from(ShareAuthorizationV5Service.RoleTemplateResult r) {
            return new RoleTemplateResponse(
                    r.templateId().toString(), r.mountId().toString(), r.roleId().toString(),
                    r.name(), r.state(), r.defaultVisible(), r.defaultRead(), r.defaultWrite(), r.version());
        }
    }
    /** 角色模板特权响应。 */
    public record RoleTemplatePrivilegeResponse(String privilegeId, String templateId, String mountId, String targetPath,
                                                boolean allowVisible, boolean allowRead, boolean allowWrite, long version) {
        public static RoleTemplatePrivilegeResponse from(ShareAuthorizationV5Service.RoleTemplatePrivilegeResult r) {
            return new RoleTemplatePrivilegeResponse(
                    r.privilegeId().toString(), r.templateId().toString(), r.mountId().toString(), r.targetPath(),
                    r.allowVisible(), r.allowRead(), r.allowWrite(), r.version());
        }
    }
    /** 模板代入批量求权响应。 */
    public record TemplateEffectiveBatchResponse(String path, boolean canVisible, boolean canRead, boolean canWrite) {
        public static TemplateEffectiveBatchResponse from(ShareAuthorizationV5Service.EffectivePermissionResult r) {
            return new TemplateEffectiveBatchResponse(r.path(), r.canVisible(), r.canRead(), r.canWrite());
        }
    }
}
