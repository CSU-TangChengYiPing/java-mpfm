package com.mpfm.backend.application.user;

import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.repository.AuthSessionRepository;
import com.mpfm.backend.infrastructure.persistence.repository.MountRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.ShareLinkRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.ShareRoleRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.SharedMountAccessRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ROOT 账号引导器，负责启动时收敛主 ROOT 账号并迁移历史 ROOT 资产归属。
 */
@Component
public class RootUserBootstrap implements CommandLineRunner {
    private static final int SINGLE_ROOT_USER_COUNT = 1;
    private static final String DEFAULT_QOS_PROFILE = "default";

    private final UserRepository userRepository;
    private final MountRepository mountRepository;
    private final AuthSessionRepository authSessionRepository;
    private final ShareRoleRepository shareRoleRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final SharedMountAccessRepository sharedMountAccessRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${MPFM_ROOT_USERNAME:root}")
    private String rootUsername;

    @Value("${MPFM_ROOT_PASSWORD:Root@123456}")
    private String rootPassword;

    public RootUserBootstrap(UserRepository userRepository,
                             MountRepository mountRepository,
                             AuthSessionRepository authSessionRepository,
                             ShareRoleRepository shareRoleRepository,
                             ShareLinkRepository shareLinkRepository,
                             SharedMountAccessRepository sharedMountAccessRepository,
                             PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.mountRepository = mountRepository;
        this.authSessionRepository = authSessionRepository;
        this.shareRoleRepository = shareRoleRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.sharedMountAccessRepository = sharedMountAccessRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        UserEntity primary = resolvePrimaryRoot();
        synchronizePrimaryRoot(primary);
        List<UserEntity> rootUsers = userRepository.findByPlatformRoleOrderByAuditUpdatedAtDesc(PlatformRole.ROOT);
        if (rootUsers.size() <= SINGLE_ROOT_USER_COUNT) {
            return;
        }
        Set<UUID> legacyRootIds = new HashSet<>();
        for (UserEntity candidate : rootUsers) {
            if (!candidate.getId().equals(primary.getId())) {
                legacyRootIds.add(candidate.getId());
            }
        }
        migrateAssetsToPrimary(primary, legacyRootIds);
        lockLegacyRoots(rootUsers, primary.getId());
    }

    private UserEntity resolvePrimaryRoot() {
        UserEntity configured = userRepository.findByUsername(rootUsername).orElse(null);
        if (configured != null) {
            return configured;
        }
        List<UserEntity> roots = userRepository.findByPlatformRoleOrderByAuditUpdatedAtDesc(PlatformRole.ROOT);
        if (!roots.isEmpty()) {
            return roots.get(0);
        }
        UserEntity root = new UserEntity();
        root.setId(UUID.randomUUID());
        root.setDisplayName("Root Admin");
        root.setPreferredLanguage("zh-CN");
        root.setFileViewMode("list");
        root.setQosProfile(DEFAULT_QOS_PROFILE);
        root.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return root;
    }

    private void synchronizePrimaryRoot(UserEntity primary) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        primary.setUsername(rootUsername);
        primary.setPasswordHash(passwordEncoder.encode(rootPassword));
        primary.setPlatformRole(PlatformRole.ROOT);
        primary.setStatus(UserStatus.ACTIVE);
        primary.setQosProfile(DEFAULT_QOS_PROFILE);
        primary.setCredentialUpdatedAt(now);
        primary.setCredentialVersion(Math.max(primary.getCredentialVersion(), 0) + 1);
        primary.setUpdatedAt(now);
        if (primary.getCreatedAt() == null) {
            primary.setCreatedAt(now);
        }
        userRepository.save(primary);
    }

    private void migrateAssetsToPrimary(UserEntity primary, Set<UUID> legacyRootIds) {
        if (legacyRootIds.isEmpty()) {
            return;
        }
        mountRepository.findAll().forEach(mount -> {
            if (legacyRootIds.contains(mount.getOwnerId())) {
                mount.setOwnerId(primary.getId());
            }
        });
        authSessionRepository.findAll().forEach(session -> {
            if (legacyRootIds.contains(session.getUserId())) {
                session.setUserId(primary.getId());
                session.setUsername(primary.getUsername());
            }
        });
        shareRoleRepository.findAll().forEach(role -> {
            if (legacyRootIds.contains(role.getCreatorUserId())) {
                role.setCreatorUserId(primary.getId());
            }
        });
        shareLinkRepository.findAll().forEach(link -> {
            if (legacyRootIds.contains(link.getCreatedByUserId())) {
                link.setCreatedByUserId(primary.getId());
            }
        });
        sharedMountAccessRepository.findAll().forEach(access -> {
            if (legacyRootIds.contains(access.getUserId())) {
                access.setUserId(primary.getId());
            }
        });
    }

    private void lockLegacyRoots(List<UserEntity> rootUsers, UUID primaryRootId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (UserEntity rootUser : rootUsers) {
            if (rootUser.getId().equals(primaryRootId)) {
                continue;
            }
            rootUser.setPlatformRole(PlatformRole.ADMIN);
            rootUser.setStatus(UserStatus.DISABLED);
            rootUser.setUpdatedAt(now);
            userRepository.save(rootUser);
        }
    }
}




