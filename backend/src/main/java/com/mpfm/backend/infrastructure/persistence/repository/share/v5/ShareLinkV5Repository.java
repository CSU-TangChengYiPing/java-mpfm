package com.mpfm.backend.infrastructure.persistence.repository.share.v5;

import com.mpfm.backend.infrastructure.persistence.entity.share.v5.ShareLinkV5Entity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * v5 授权链接仓储，提供按 token 与主键查询能力。
 */
public interface ShareLinkV5Repository extends JpaRepository<ShareLinkV5Entity, UUID> {
    Optional<ShareLinkV5Entity> findByToken(String token);
}

