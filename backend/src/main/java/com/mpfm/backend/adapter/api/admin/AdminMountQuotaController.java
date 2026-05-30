package com.mpfm.backend.adapter.api.admin;

import com.mpfm.backend.application.mount.MountQuotaService;
import com.mpfm.backend.common.audit.AuditAction;
import com.mpfm.backend.common.audit.AuditEvent;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端挂载配额控制器，负责默认配额与单挂载配额覆盖的维护接口。
 */
@RestController
@RequestMapping("/api/v1/admin/mounts/quota")
@PreAuthorize("hasRole('ADMIN') or hasRole('ROOT')")
public class AdminMountQuotaController {
    private final MountQuotaService mountQuotaService;

    public AdminMountQuotaController(MountQuotaService mountQuotaService) {
        this.mountQuotaService = mountQuotaService;
    }

    @GetMapping("/default")
    public DefaultQuotaResponse getDefaultQuota() {
        return new DefaultQuotaResponse(mountQuotaService.getDefaultCapacityBytes());
    }

    @PostMapping("/default")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "mount_quota_default_update", target = "mount_quota", riskLevel = "high")
    public DefaultQuotaResponse updateDefaultQuota(@RequestBody DefaultQuotaRequest request) {
        return new DefaultQuotaResponse(mountQuotaService.updateDefaultCapacityBytes(request.defaultCapacityBytes()));
    }

    @PostMapping("/{mountId}")
    @AuditAction(event = AuditEvent.DEMO_SUCCESS, action = "mount_quota_override_update", target = "mount_quota", riskLevel = "high")
    public MountQuotaResponse updateMountQuota(@PathVariable UUID mountId, @RequestBody MountQuotaRequest request) {
        MountQuotaService.MountQuotaResult result =
                mountQuotaService.updateMountCapacity(mountId, request.capacityBytes(), request.useDefault());
        return new MountQuotaResponse(result.mountId().toString(), result.customCapacityBytes(), result.effectiveCapacityBytes());
    }

    /** 默认配额更新请求，`defaultCapacityBytes` 表示平台默认容量（字节）。 */
    public record DefaultQuotaRequest(@NotNull Long defaultCapacityBytes) { }
    /** 挂载配额更新请求，`useDefault=true` 时忽略 `capacityBytes` 并回退默认配额。 */
    public record MountQuotaRequest(Long capacityBytes, boolean useDefault) { }
    /** 默认配额响应，返回当前生效的平台默认容量（字节）。 */
    public record DefaultQuotaResponse(long defaultCapacityBytes) { }
    /** 挂载配额响应，返回挂载标识、自定义容量及最终生效容量。 */
    public record MountQuotaResponse(String mountId, Long customCapacityBytes, long effectiveCapacityBytes) { }
}



