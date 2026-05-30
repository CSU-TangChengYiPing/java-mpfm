package com.mpfm.backend.infrastructure.persistence.repository;

import com.mpfm.backend.infrastructure.persistence.entity.QosPolicyAuditEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * QoS 审计仓储，支持按时间倒序读取策略治理记录。
 */
public interface QosPolicyAuditRepository extends JpaRepository<QosPolicyAuditEntity, Long> {
    List<QosPolicyAuditEntity> findTop200ByOrderByCreatedAtDesc();
}

