package com.mpfm.backend.infrastructure.persistence.repository.share;

import com.mpfm.backend.infrastructure.persistence.entity.share.ShareLinkEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 共享链接仓储接口，提供按挂载与 token 查询共享链接能力。
 */
public interface ShareLinkRepository extends JpaRepository<ShareLinkEntity, UUID> {
    List<ShareLinkEntity> findByMountId(UUID mountId);
    Optional<ShareLinkEntity> findByToken(String token);
}





