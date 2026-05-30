package com.mpfm.backend.infrastructure.persistence.repository;

import com.mpfm.backend.infrastructure.persistence.entity.UserTransferGovernanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户传输治理仓储，按用户名维护暂停上传/下载状态。
 */
public interface UserTransferGovernanceRepository extends JpaRepository<UserTransferGovernanceEntity, String> {
}

