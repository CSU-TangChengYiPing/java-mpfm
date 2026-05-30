package com.mpfm.backend.infrastructure.persistence.repository;

import com.mpfm.backend.infrastructure.persistence.entity.QosMountBindingEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 挂载级 QoS 绑定仓储。
 */
public interface QosMountBindingRepository extends JpaRepository<QosMountBindingEntity, UUID> {
    Optional<QosMountBindingEntity> findByMountId(UUID mountId);
}

