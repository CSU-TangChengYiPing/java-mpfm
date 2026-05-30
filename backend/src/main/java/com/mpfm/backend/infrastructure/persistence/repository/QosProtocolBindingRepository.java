package com.mpfm.backend.infrastructure.persistence.repository;

import com.mpfm.backend.infrastructure.persistence.entity.QosProtocolBindingEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 协议级 QoS 绑定仓储。
 */
public interface QosProtocolBindingRepository extends JpaRepository<QosProtocolBindingEntity, String> {
    Optional<QosProtocolBindingEntity> findByProtocol(String protocol);
}

