package com.mpfm.backend.infrastructure.persistence.repository;

import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 挂载仓储接口，提供按所有者、状态与软删除时间窗口的挂载查询能力。
 */
public interface MountRepository extends JpaRepository<MountEntity, UUID> {
    List<MountEntity> findByOwnerIdAndStateNot(UUID ownerId, String state);
    List<MountEntity> findByStateNot(String state);
    Optional<MountEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
    Optional<MountEntity> findByOwnerIdAndNameAndStateNot(UUID ownerId, String name, String state);
    Optional<MountEntity> findByNameAndStateNot(String name, String state);
    List<MountEntity> findByStateAndDeletedAtBefore(String state, OffsetDateTime deletedAt);
}





