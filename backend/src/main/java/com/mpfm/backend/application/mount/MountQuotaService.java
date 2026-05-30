package com.mpfm.backend.application.mount;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.repository.MountRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 挂载配额服务，负责默认配额与单挂载覆盖配额的读写规则。
 */
@Service
public class MountQuotaService {
    private static final long ZERO_BYTES = 0L;
    private final MountRepository mountRepository;
    private final AtomicLong defaultCapacityBytes;

    public MountQuotaService(MountRepository mountRepository,
                             @Value("${mpfm.mount.default-capacity-bytes:1073741824}") long defaultCapacityBytes) {
        this.mountRepository = mountRepository;
        this.defaultCapacityBytes = new AtomicLong(Math.max(defaultCapacityBytes, 0L));
    }

    public long getDefaultCapacityBytes() {
        return defaultCapacityBytes.get();
    }

    public long updateDefaultCapacityBytes(long value) {
        if (value < ZERO_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "default capacity must be >= 0");
        }
        defaultCapacityBytes.set(value);
        return value;
    }

    public MountQuotaResult updateMountCapacity(UUID mountId, Long capacityBytes, boolean useDefault) {
        MountEntity mount = mountRepository.findById(mountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "mount not found"));
        if (useDefault) {
            mount.setCapacityBytes(null);
        } else {
            if (capacityBytes == null || capacityBytes < ZERO_BYTES) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "capacity must be >= 0");
            }
            mount.setCapacityBytes(capacityBytes);
        }
        mount.setUpdatedAt(OffsetDateTime.now());
        mountRepository.save(mount);
        return new MountQuotaResult(mount.getId(), mount.getCapacityBytes(), effectiveCapacityBytes(mount));
    }

    public long effectiveCapacityBytes(MountEntity mount) {
        if (mount.getCapacityBytes() != null) {
            return Math.max(ZERO_BYTES, mount.getCapacityBytes());
        }
        return Math.max(ZERO_BYTES, defaultCapacityBytes.get());
    }

    /** 挂载配额结果模型，返回自定义值与最终生效容量。 */
    public record MountQuotaResult(UUID mountId, Long customCapacityBytes, long effectiveCapacityBytes) { }
}


