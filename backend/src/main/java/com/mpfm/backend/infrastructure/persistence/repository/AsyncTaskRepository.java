package com.mpfm.backend.infrastructure.persistence.repository;

import com.mpfm.backend.infrastructure.persistence.entity.AsyncTaskEntity;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 异步任务仓储接口，负责任务实体的基础持久化访问。
 */
public interface AsyncTaskRepository extends JpaRepository<AsyncTaskEntity, UUID> {
    List<AsyncTaskEntity> findByOperatorOrderByUpdatedAtDesc(String operator);
    List<AsyncTaskEntity> findByOperatorAndStatusIn(String operator, List<String> statuses);
    List<AsyncTaskEntity> findByStatusIn(List<String> statuses);
    long deleteByOperatorAndStatusIn(String operator, List<String> statuses);
}





