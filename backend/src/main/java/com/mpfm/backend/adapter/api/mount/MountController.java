package com.mpfm.backend.adapter.api.mount;

import com.mpfm.backend.application.mount.MountApplicationService;
import com.mpfm.backend.common.audit.AuditAction;
import com.mpfm.backend.common.audit.AuditEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 挂载管理控制器，提供挂载生命周期管理、健康查询与能力详情查询接口。
 */
@RestController
@RequestMapping("/api/v1/mounts")
public class MountController {
    private static final String AUDIT_TARGET_MOUNT = "mount";

    private final MountApplicationService mountApplicationService;

    public MountController(MountApplicationService mountApplicationService) {
        this.mountApplicationService = mountApplicationService;
    }

    @PostMapping
    public MountResponse create(@RequestBody CreateMountRequest request, Principal principal) {
        return MountResponse.from(mountApplicationService.createMount(
                principal.getName(), request.name(), request.protocol(), request.enabled(), request.sharedEnabled(),
                request.host(), request.port(), request.username(), request.password(), request.remoteRoot(), request.localRoot()));
    }

    @PostMapping("/test-connection")
    public MountConnectionCheckResponse testConnection(@RequestBody TestConnectionRequest request, Principal principal) {
        return MountConnectionCheckResponse.from(mountApplicationService.testConnection(
                principal.getName(), request.protocol(), request.host(), request.port(), request.username(), request.password(),
                request.remoteRoot(), request.localRoot()));
    }

    @GetMapping
    public List<MountResponse> list(Principal principal) {
        return mountApplicationService.listMyMounts(principal.getName()).stream().map(MountResponse::from).toList();
    }

    @GetMapping("/{mountId}")
    public MountResponse detail(@PathVariable UUID mountId, Principal principal) {
        return MountResponse.from(mountApplicationService.getMyMount(principal.getName(), mountId));
    }

    @PutMapping("/{mountId}")
    public MountResponse update(@PathVariable UUID mountId, @RequestBody UpdateMountRequest request, Principal principal) {
        return MountResponse.from(mountApplicationService.updateMount(
                principal.getName(), mountId, request.name(), request.sharedEnabled(),
                request.host(), request.port(), request.username(), request.password(), request.remoteRoot()));
    }

    @PostMapping("/{mountId}/enable")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "mount_enable", target = AUDIT_TARGET_MOUNT, riskLevel = "medium")
    public MountResponse enable(@PathVariable UUID mountId, Principal principal) {
        return MountResponse.from(mountApplicationService.enable(principal.getName(), mountId));
    }

    @PostMapping("/{mountId}/disable")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "mount_disable", target = AUDIT_TARGET_MOUNT, riskLevel = "medium")
    public MountResponse disable(@PathVariable UUID mountId, Principal principal) {
        return MountResponse.from(mountApplicationService.disable(principal.getName(), mountId));
    }

    @DeleteMapping("/{mountId}")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "mount_soft_delete", target = AUDIT_TARGET_MOUNT, riskLevel = "high")
    public OperationResponse delete(@PathVariable UUID mountId, Principal principal) {
        mountApplicationService.softDelete(principal.getName(), mountId);
        return new OperationResponse("delete", "success");
    }

    @PostMapping("/{mountId}/restore")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "mount_restore", target = AUDIT_TARGET_MOUNT, riskLevel = "high")
    public MountResponse restore(@PathVariable UUID mountId, Principal principal) {
        return MountResponse.from(mountApplicationService.restore(principal.getName(), mountId));
    }

    @PostMapping("/purge-due")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ROOT')")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "mount_purge_due", target = AUDIT_TARGET_MOUNT, riskLevel = "high")
    public PurgeResponse purgeDue() {
        return PurgeResponse.from(mountApplicationService.purgeDueSoftDeletedMounts());
    }

    @GetMapping("/{mountId}/health")
    public MountHealthResponse health(@PathVariable UUID mountId, Principal principal) {
        return MountHealthResponse.from(mountApplicationService.health(principal.getName(), mountId));
    }

    @GetMapping("/{mountId}/details")
    public MountDetailsResponse details(@PathVariable UUID mountId, Principal principal) {
        return MountDetailsResponse.from(mountApplicationService.details(principal.getName(), mountId));
    }

    @GetMapping("/{mountId}/capabilities")
    public MountApplicationService.MountCapabilities capabilities(@PathVariable UUID mountId, Principal principal) {
        return mountApplicationService.capabilities(principal.getName(), mountId);
    }

    /** 创建挂载请求，支持 local/sftp/webdav 协议。 */
    public record CreateMountRequest(@NotBlank String name,
                                     @NotBlank String protocol,
                                     boolean enabled,
                                     @JsonProperty("shared_enabled") boolean sharedEnabled,
                                     String host,
                                     Integer port,
                                     String username,
                                     String password,
                                     @JsonProperty("remote_root") String remoteRoot,
                                     @JsonProperty("local_root") String localRoot) { }
    /** 创建前连接探测请求，仅验证连通性与根目录可访问性，不落库。 */
    public record TestConnectionRequest(@NotBlank String protocol,
                                        String host,
                                        Integer port,
                                        String username,
                                        String password,
                                        @JsonProperty("remote_root") String remoteRoot,
                                        @JsonProperty("local_root") String localRoot) { }
    /** 挂载更新请求：local 仅更新名称与共享开关；远程挂载可更新连接参数。 */
    public record UpdateMountRequest(@NotBlank String name,
                                     @JsonProperty("shared_enabled") boolean sharedEnabled,
                                     String host,
                                     Integer port,
                                     String username,
                                     String password,
                                     @JsonProperty("remote_root") String remoteRoot) { }
    /** 通用操作响应，返回动作名称与执行状态。 */
    public record OperationResponse(String action, String status) { }

    /** 挂载摘要响应，兼容新旧字段口径。 */
    public record MountResponse(
            String id,
            String mountId,
            String protocol,
            String type,
            String name,
            String root,
            String virtualPath,
            boolean enabled,
            String state,
            @JsonProperty("shared_enabled") boolean sharedEnabled,
            @JsonProperty("owner_user") String ownerUser,
            @JsonProperty("can_manage") boolean canManage) {
        static MountResponse from(MountApplicationService.MountResult mount) {
            boolean enabled = "enabled".equalsIgnoreCase(mount.state());
            return new MountResponse(
                    mount.mountId().toString(),
                    mount.mountId().toString(),
                    mount.type(),
                    mount.type(),
                    mount.name(),
                    mount.physicalRoot(),
                    mount.virtualPath(),
                    enabled,
                    mount.state(),
                    mount.sharedEnabled(),
                    mount.ownerUser(),
                    mount.canManage());
        }
    }
    /** 过期清理响应，返回本次清理数量与到期截止时间。 */
    public record PurgeResponse(int purgedCount, String dueBefore) {
        static PurgeResponse from(MountApplicationService.PurgeResult result) {
            return new PurgeResponse(result.purgedCount(), result.dueBefore());
        }
    }
    /** 挂载健康响应，返回挂载标识、健康等级与原因说明。 */
    public record MountHealthResponse(String mountId, String health, String reason) {
        static MountHealthResponse from(MountApplicationService.MountHealth health) {
            return new MountHealthResponse(health.mountId().toString(), health.health(), health.reason());
        }
    }
    /** 挂载详情响应，包含时间字段与容量统计字段。 */
    public record MountDetailsResponse(String mountId, String name, String type, String state, String createdAt, String updatedAt,
                                       long usedBytes, long totalBytes, long freeBytes) {
        static MountDetailsResponse from(MountApplicationService.MountDetails details) {
            return new MountDetailsResponse(
                    details.mountId().toString(), details.name(), details.type(), details.state(),
                    details.createdAt(), details.updatedAt(), details.usedBytes(), details.totalBytes(), details.freeBytes()
            );
        }
    }
    /** 连接探测响应，返回健康状态与原因。 */
    public record MountConnectionCheckResponse(String protocol, String health, String reason) {
        static MountConnectionCheckResponse from(MountApplicationService.ConnectionCheckResult result) {
            return new MountConnectionCheckResponse(result.protocol(), result.health(), result.reason());
        }
    }
}




