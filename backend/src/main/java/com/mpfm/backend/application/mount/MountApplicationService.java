package com.mpfm.backend.application.mount;

import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 挂载应用服务，负责挂载生命周期、状态维护与详情查询流程编排。
 */
@Service
public class MountApplicationService {

    private final MountLifecycleService lifecycleService;
    private final MountInspectService inspectService;

    public MountApplicationService(MountLifecycleService lifecycleService, MountInspectService inspectService) {
        this.lifecycleService = lifecycleService;
        this.inspectService = inspectService;
    }

    public MountResult createMount(String username, String name, String protocol, boolean enabled, boolean sharedEnabled,
                                   String host, Integer port, String mountUsername, String password, String remoteRoot, String localRoot) {
        return lifecycleService.createMount(username, name, protocol, enabled, sharedEnabled, host, port, mountUsername, password, remoteRoot, localRoot);
    }
    public ConnectionCheckResult testConnection(String username, String protocol,
                                                String host, Integer port, String mountUsername, String password, String remoteRoot, String localRoot) {
        return lifecycleService.testConnection(username, protocol, host, port, mountUsername, password, remoteRoot, localRoot);
    }
    public List<MountResult> listMyMounts(String username) { return lifecycleService.listMyMounts(username); }
    public MountResult getMyMount(String username, UUID mountId) { return lifecycleService.getMyMount(username, mountId); }
    public MountResult updateMount(String username, UUID mountId, String name, boolean sharedEnabled,
                                   String host, Integer port, String mountUsername, String password, String remoteRoot) {
        return lifecycleService.updateMount(username, mountId, name, sharedEnabled, host, port, mountUsername, password, remoteRoot);
    }

    // 启用、禁用、软删除、恢复、清理过期软删除、健康检查、详情查询、能力查询、要求已启用挂载实体
    public MountResult enable(String username, UUID mountId) { return lifecycleService.enable(username, mountId); }
    public MountResult disable(String username, UUID mountId) { return lifecycleService.disable(username, mountId); }
    public MountResult softDelete(String username, UUID mountId) { return lifecycleService.softDelete(username, mountId); }
    public MountResult restore(String username, UUID mountId) { return lifecycleService.restore(username, mountId); }
    public PurgeResult purgeDueSoftDeletedMounts() { return inspectService.purgeDueSoftDeletedMounts(); }
    public MountHealth health(String username, UUID mountId) { return inspectService.health(username, mountId); }
    public MountDetails details(String username, UUID mountId) { return inspectService.details(username, mountId); }
    public MountCapabilities capabilities(String username, UUID mountId) { return inspectService.capabilities(username, mountId); }
    public MountEntity requireOwnedEnabledMount(String username, UUID mountId) { return lifecycleService.requireOwnedEnabledMount(username, mountId); }

    /** 挂载摘要模型，返回挂载标识、路径、归属与管理权限等基础信息。 */
    public record MountResult(UUID mountId, String type, String name, String physicalRoot, String virtualPath, String state, boolean sharedEnabled,
                              String ownerUser, boolean canManage) { }
    /** 过期清理结果模型，返回清理数量与截止时间。 */
    public record PurgeResult(int purgedCount, String dueBefore) { }
    /** 健康检查结果模型，返回健康等级与原因。 */
    public record MountHealth(UUID mountId, String health, String reason) { }
    /** 挂载详情模型，返回时间字段与容量统计字段。 */
    public record MountDetails(UUID mountId, String name, String type, String state, String createdAt, String updatedAt,
                               long usedBytes, long totalBytes, long freeBytes) { }
    /** 创建前连接探测结果，要求返回可连通且远程根目录可访问。 */
    public record ConnectionCheckResult(String protocol, String health, String reason) { }
    /** 挂载能力模型，声明当前挂载支持的能力开关。 */
    public record MountCapabilities(String version, Map<String, Object> core, Map<String, Object> extended, Map<String, Object> constraints) {
        public MountCapabilities(String version, Map<String, Object> core,
                                 Map<String, Object> extended, Map<String, Object> constraints) {
            this.version = version;
            this.core = core == null ? Map.of() : Map.copyOf(core);
            this.extended = extended == null ? Map.of() : Map.copyOf(extended);
            this.constraints = constraints == null ? Map.of() : Map.copyOf(constraints);
        }
    }
}




