package com.mpfm.backend.application.mount;

import com.mpfm.backend.application.driver.base.DriverFactory;
import com.mpfm.backend.application.driver.base.DriverCapabilitySnapshot;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.repository.MountRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class MountInspectService {
    private static final String STATE_SOFT_DELETED = "soft_deleted";
    private static final String STATE_PURGED = "purged";

    private final MountRepository mountRepository;
    private final MountLifecycleService lifecycleService;
    private final MountQuotaService mountQuotaService;
    private final DriverFactory driverFactory;

    MountInspectService(MountRepository mountRepository,
                        MountLifecycleService lifecycleService,
                        MountQuotaService mountQuotaService,
                        DriverFactory driverFactory) {
        this.mountRepository = mountRepository;
        this.lifecycleService = lifecycleService;
        this.mountQuotaService = mountQuotaService;
        this.driverFactory = driverFactory;
    }

    MountApplicationService.PurgeResult purgeDueSoftDeletedMounts() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(30);
        List<MountEntity> due = mountRepository.findByStateAndDeletedAtBefore(STATE_SOFT_DELETED, threshold);
        int purged = 0;
        for (MountEntity mount : due) {
            mount.setState(STATE_PURGED);
            mount.setUpdatedAt(OffsetDateTime.now());
            mountRepository.save(mount);
            purged++;
        }
        return new MountApplicationService.PurgeResult(purged, threshold.truncatedTo(ChronoUnit.SECONDS).toString());
    }

    MountApplicationService.MountHealth health(String username, UUID mountId) {
        MountEntity mount = lifecycleService.requireOwnedOrAdminMount(username, mountId);
        boolean exists = Files.exists(Path.of(mount.getPhysicalRoot()));
        return new MountApplicationService.MountHealth(mount.getId(), exists ? "available" : "unavailable", exists ? null : "root path missing");
    }

    MountApplicationService.MountDetails details(String username, UUID mountId) {
        MountEntity mount = lifecycleService.requireOwnedOrAdminMount(username, mountId);
        Path root = Path.of(mount.getPhysicalRoot());
        long usedBytes = 0L;
        try {
            if (Files.exists(root)) {
                usedBytes = Files.walk(root)
                        .filter(Files::isRegularFile)
                        .mapToLong(path -> {
                            try {
                                return Files.size(path);
                            } catch (Exception ignore) {
                                return 0L;
                            }
                        }).sum();
            }
        } catch (Exception ignore) {
            usedBytes = 0L;
        }
        long totalBytes = mountQuotaService.effectiveCapacityBytes(mount);
        long freeBytes = totalBytes <= 0L ? 0L : Math.max(0L, totalBytes - usedBytes);
        return new MountApplicationService.MountDetails(
                mount.getId(), mount.getName(), mount.getType(), mount.getState(),
                mount.getCreatedAt().toString(), mount.getUpdatedAt().toString(), usedBytes, totalBytes, freeBytes);
    }

    MountApplicationService.MountCapabilities capabilities(String username, UUID mountId) {
        MountEntity mount = lifecycleService.requireOwnedOrAdminMount(username, mountId);
        DriverCapabilitySnapshot snapshot = DriverCapabilitySnapshot.from(mount.getType(), driverFactory.resolve(mount.getType()).capability());
        return new MountApplicationService.MountCapabilities(
                MountCapabilityKeys.VERSION,
                Map.ofEntries(
                        Map.entry(MountCapabilityKeys.CORE_LIST_TREE, true), Map.entry(MountCapabilityKeys.CORE_STAT, true),
                        Map.entry(MountCapabilityKeys.CORE_GET_BY_PATH, true), Map.entry(MountCapabilityKeys.CORE_UPLOAD, snapshot.put()),
                        Map.entry(MountCapabilityKeys.CORE_DOWNLOAD, snapshot.get()), Map.entry(MountCapabilityKeys.CORE_MKDIR, snapshot.makeDir()),
                        Map.entry(MountCapabilityKeys.CORE_DELETE, snapshot.remove()), Map.entry(MountCapabilityKeys.CORE_LINK, snapshot.link()),
                        Map.entry(MountCapabilityKeys.CORE_RENAME, snapshot.rename()), Map.entry(MountCapabilityKeys.CORE_MOVE, snapshot.move()),
                        Map.entry(MountCapabilityKeys.CORE_COPY, snapshot.copy()), Map.entry(MountCapabilityKeys.CORE_HEALTH_BASIC, true)),
                Map.ofEntries(
                        Map.entry(MountCapabilityKeys.EXT_PUT_URL, snapshot.directUpload()), Map.entry(MountCapabilityKeys.EXT_BATCH_UPLOAD, snapshot.put()),
                        Map.entry(MountCapabilityKeys.EXT_COPY, snapshot.copy()),
                        Map.entry(MountCapabilityKeys.EXT_SYMLINK_RESOLVE, true), Map.entry(MountCapabilityKeys.EXT_ASYNC_TASK_RESULT, true),
                        Map.entry(MountCapabilityKeys.EXT_STORAGE_DETAILS, true), Map.entry(MountCapabilityKeys.EXT_ARCHIVE_META, false),
                        Map.entry(MountCapabilityKeys.EXT_ARCHIVE_PREVIEW, false), Map.entry(MountCapabilityKeys.EXT_ARCHIVE_READ_INNER, false),
                        Map.entry(MountCapabilityKeys.EXT_ARCHIVE_EXTRACT, false), Map.entry(MountCapabilityKeys.EXT_ARCHIVE_COMPRESS, false)),
                Map.of(MountCapabilityKeys.CONSTRAINT_NO_UPLOAD, !snapshot.put(),
                        MountCapabilityKeys.CONSTRAINT_NO_OVERWRITE_UPLOAD, false,
                        MountCapabilityKeys.CONSTRAINT_ONLY_PROXY, snapshot.onlyProxy(),
                        MountCapabilityKeys.CONSTRAINT_NO_LINK_URL, snapshot.noLinkUrl(),
                        MountCapabilityKeys.CONSTRAINT_PREFER_PROXY, snapshot.preferProxy(),
                        MountCapabilityKeys.CONSTRAINT_MAX_UPLOAD_SIZE_MB, 0,
                        MountCapabilityKeys.CONSTRAINT_RATE_LIMIT_PROFILE, MountCapabilityKeys.DEFAULT_RATE_LIMIT_PROFILE)
        );
    }
}


