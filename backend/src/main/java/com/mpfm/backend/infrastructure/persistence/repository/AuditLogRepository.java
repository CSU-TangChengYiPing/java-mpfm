package com.mpfm.backend.infrastructure.persistence.repository;

import com.mpfm.backend.infrastructure.persistence.entity.AuditLogEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 审计日志仓储接口，提供按创建时间区间倒序检索审计事件能力。
 */
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
    List<AuditLogEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(OffsetDateTime from, OffsetDateTime to);
}





