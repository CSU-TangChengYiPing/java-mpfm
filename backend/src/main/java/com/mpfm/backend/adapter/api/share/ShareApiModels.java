package com.mpfm.backend.adapter.api.share;

import com.mpfm.backend.application.share.ShareApplicationService;
import com.mpfm.backend.application.share.SharePermissionService;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 共享域接口模型集合，定义角色、策略、链接与权限查询相关请求/响应结构。
 */
public class ShareApiModels {

    /** 共享角色创建请求，`name` 为角色名称，`roleExpiresAt` 为可选到期时间。 */
    public record CreateRoleRequest(@NotBlank String name, OffsetDateTime roleExpiresAt) { }
    /** 共享角色更新请求，允许调整名称与角色到期时间。 */
    public record UpdateRoleRequest(@NotBlank String name, OffsetDateTime roleExpiresAt) { }
    /** 路径策略项，按路径模式声明可见/可读/可写权限位。 */
    public record PolicyItem(String pathPattern, boolean canVisible, boolean canRead, boolean canWrite) { }
    /** 路径策略更新请求，构造时将空输入归一化为不可变空集合。 */
    public record UpdatePolicyRequest(List<PolicyItem> items) {
        public UpdatePolicyRequest(List<PolicyItem> items) {
            this.items = items == null ? List.of() : List.copyOf(items);
        }
    }
    /** 共享链接创建请求，支持设置生效窗口、最大使用次数与角色临时有效期。 */
    public record CreateLinkRequest(UUID roleId, OffsetDateTime startAt, OffsetDateTime expireAt, Integer maxUses,
                                    OffsetDateTime roleStartAt, OffsetDateTime roleExpireAt) { }
    /** 共享链接更新请求，用于调整生效窗口与最大使用次数。 */
    public record UpdateLinkRequest(OffsetDateTime startAt, OffsetDateTime expireAt, Integer maxUses) { }
    /** 共享链接解析请求，`token` 为外部访问令牌。 */
    public record ResolveLinkRequest(String token) { }
    /** 共享挂载切换角色请求，`roleId` 指向目标共享角色。 */
    public record SwitchRoleRequest(UUID roleId) { }
    /** 共享授权范围写入请求。 */
    public record UpsertGrantRequest(String role, List<String> pathScopes, List<String> permissions) { }

    /** 共享角色响应，返回角色标识、所属挂载、状态与过期时间。 */
    public record RoleResponse(String roleId, String mountId, String name, String state, String roleExpiresAt) {
        public static RoleResponse from(ShareApplicationService.ShareRoleResult r) {
            return new RoleResponse(r.roleId().toString(), r.mountId().toString(), r.name(), r.state(), r.roleExpiresAt());
        }
    }

    /** 路径策略响应，返回策略标识、所属角色与权限位。 */
    public record PolicyResponse(String policyId, String roleId, String pathPattern,
                                 boolean canVisible, boolean canRead, boolean canWrite) {
        public static PolicyResponse from(ShareApplicationService.PathPolicyResult r) {
            return new PolicyResponse(r.policyId().toString(), r.roleId().toString(), r.pathPattern(),
                    r.canVisible(), r.canRead(), r.canWrite());
        }
    }

    /** 共享链接响应，返回链接状态、可用窗口、使用次数及上限。 */
    public record LinkResponse(String linkId, String mountId, String roleId, String token, String state,
                               String startAt, String expireAt, int usedCount, Integer maxUses) {
        public static LinkResponse from(ShareApplicationService.ShareLinkResult l) {
            return new LinkResponse(l.linkId().toString(), l.mountId().toString(), l.roleId().toString(), l.token(),
                    l.state(), l.startAt(), l.expireAt(), l.usedCount(), l.maxUses());
        }
    }

    /** 共享链接解析/角色切换响应，返回目标挂载、角色、状态与令牌。 */
    public record ResolveResponse(String mountId, String roleId, String state, String token) {
        public static ResolveResponse from(ShareApplicationService.ResolveResult r) {
            return new ResolveResponse(r.mountId().toString(), r.roleId().toString(), r.state(), r.token());
        }
    }

    /** 有效权限响应，返回指定路径在当前共享上下文下的最终权限。 */
    public record PermissionResponse(String path, boolean canVisible, boolean canRead, boolean canWrite, String roleId) {
        public static PermissionResponse from(ShareApplicationService.EffectivePermissionResult r) {
            return new PermissionResponse(r.path(), r.canVisible(), r.canRead(), r.canWrite(),
                    r.roleId() == null ? null : r.roleId().toString());
        }
    }

    /** 通用操作响应，返回动作名称与执行状态。 */
    public record OperationResponse(String action, String status) { }

    /** 共享审计响应。 */
    public record AuditResponse(String id, String mountId, String action, String actor, String result, String detail, String occurredAt) {
        public static AuditResponse from(SharePermissionService.ShareAuditView r) {
            return new AuditResponse(r.id().toString(), r.mountId().toString(), r.action(), r.actor(), r.result(), r.detail(), r.occurredAt());
        }
    }

    /** 共享授权范围响应。 */
    public record GrantResponse(String id, String mountId, String role, List<String> pathScopes, List<String> permissions,
                                String createdBy, String createdAt, String updatedAt) {
        public static GrantResponse from(SharePermissionService.ShareGrantView r) {
            return new GrantResponse(r.id().toString(), r.mountId().toString(), r.role(), r.pathScopes(), r.permissions(),
                    r.createdBy(), r.createdAt(), r.updatedAt());
        }
    }

    /** 共享权限预览节点响应。 */
    public record PreviewNodeResponse(String path, boolean isDir, List<String> permissions, boolean visible, int depth) {
        public static PreviewNodeResponse from(SharePermissionService.SharePreviewNodeView r) {
            return new PreviewNodeResponse(r.path(), r.isDir(), r.permissions(), r.visible(), r.depth());
        }
    }
}




