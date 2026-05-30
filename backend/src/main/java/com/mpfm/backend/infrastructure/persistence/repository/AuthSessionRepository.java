package com.mpfm.backend.infrastructure.persistence.repository;

import com.mpfm.backend.infrastructure.persistence.entity.AuthSessionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 认证会话仓储接口，提供按刷新令牌哈希、用户名和状态的会话查询能力。
 */
public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {
    List<AuthSessionEntity> findByRefreshHashAndStatusOrderByCreatedAtDesc(String refreshHash, String status);
    List<AuthSessionEntity> findByUsernameAndStatus(String username, String status);
    Optional<AuthSessionEntity> findByIdAndUsernameAndStatus(UUID id, String username, String status);
    Optional<AuthSessionEntity> findByIdAndStatus(UUID id, String status);
}





