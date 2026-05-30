package com.mpfm.backend.application.mount;

import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 挂载生命周期门面，仅负责编排读写协作器。 */
@Service
class MountLifecycleService {
    private final MountLifecycleReadService readService;
    private final MountLifecycleWriteService writeService;

    MountLifecycleService(MountLifecycleReadService readService, MountLifecycleWriteService writeService) {
        this.readService = readService;
        this.writeService = writeService;
    }

    MountApplicationService.MountResult createMount(String username, String name, String protocol, boolean enabled, boolean sharedEnabled,
                                                    String host, Integer port, String mountUsername, String password, String remoteRoot, String localRoot) {
        return writeService.createMount(username, name, protocol, enabled, sharedEnabled, host, port, mountUsername, password, remoteRoot, localRoot);
    }
    MountApplicationService.ConnectionCheckResult testConnection(String username, String protocol,
                                                                 String host, Integer port, String mountUsername, String password, String remoteRoot, String localRoot) {
        return writeService.testConnection(username, protocol, host, port, mountUsername, password, remoteRoot, localRoot);
    }

    List<MountApplicationService.MountResult> listMyMounts(String username) {
        return readService.listMyMounts(username);
    }

    MountApplicationService.MountResult getMyMount(String username, UUID mountId) {
        return readService.getMyMount(username, mountId);
    }

    MountApplicationService.MountResult updateMount(String username, UUID mountId, String name, boolean sharedEnabled,
                                                    String host, Integer port, String mountUsername, String password, String remoteRoot) {
        return writeService.updateMount(username, mountId, name, sharedEnabled, host, port, mountUsername, password, remoteRoot);
    }

    MountApplicationService.MountResult enable(String username, UUID mountId) {
        return writeService.enable(username, mountId);
    }

    MountApplicationService.MountResult disable(String username, UUID mountId) {
        return writeService.disable(username, mountId);
    }

    MountApplicationService.MountResult softDelete(String username, UUID mountId) {
        return writeService.softDelete(username, mountId);
    }

    MountApplicationService.MountResult restore(String username, UUID mountId) {
        return writeService.restore(username, mountId);
    }

    MountEntity requireOwnedEnabledMount(String username, UUID mountId) {
        return readService.requireOwnedEnabledMount(username, mountId);
    }

    MountEntity requireOwnedOrAdminMount(String username, UUID mountId) {
        return readService.requireOwnedOrAdminMount(username, mountId);
    }
}
