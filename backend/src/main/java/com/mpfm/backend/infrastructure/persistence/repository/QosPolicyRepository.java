package com.mpfm.backend.infrastructure.persistence.repository;

import com.mpfm.backend.infrastructure.persistence.entity.QosPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * QoS 策略仓储，提供策略实体的持久化查询能力。
 */
public interface QosPolicyRepository extends JpaRepository<QosPolicyEntity, String> {
}

