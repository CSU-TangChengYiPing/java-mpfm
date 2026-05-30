package com.mpfm.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.mpfm.backend.application.user.PlatformRole;
import com.mpfm.backend.application.user.RootUserBootstrap;
import com.mpfm.backend.application.user.UserStatus;
import com.mpfm.backend.infrastructure.persistence.entity.AuthSessionEntity;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.ShareLinkEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.ShareRoleEntity;
import com.mpfm.backend.infrastructure.persistence.entity.share.SharedMountAccessEntity;
import com.mpfm.backend.infrastructure.persistence.repository.AuthSessionRepository;
import com.mpfm.backend.infrastructure.persistence.repository.MountRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.ShareLinkRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.ShareRoleRepository;
import com.mpfm.backend.infrastructure.persistence.repository.share.SharedMountAccessRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RootBootstrapIntegrationTests {

    @Autowired
    private RootUserBootstrap rootUserBootstrap;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MountRepository mountRepository;
    @Autowired
    private AuthSessionRepository authSessionRepository;
    @Autowired
    private ShareRoleRepository shareRoleRepository;
    @Autowired
    private ShareLinkRepository shareLinkRepository;
    @Autowired
    private SharedMountAccessRepository sharedMountAccessRepository;

    @Test
    void bootstrapShouldKeepSingleRootAndMigrateLegacyRootAssets() throws Exception {
        sharedMountAccessRepository.deleteAll();
        shareLinkRepository.deleteAll();
        shareRoleRepository.deleteAll();
        authSessionRepository.deleteAll();
        mountRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity legacyRoot = createUser("legacy_root", PlatformRole.ROOT, UserStatus.ACTIVE, OffsetDateTime.now(ZoneOffset.UTC).minusDays(2));
        UserEntity staleRoot = createUser("stale_root", PlatformRole.ROOT, UserStatus.ACTIVE, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        UserEntity configuredUser = createUser("cfg_root", PlatformRole.USER, UserStatus.ACTIVE, OffsetDateTime.now(ZoneOffset.UTC));
        legacyRoot = userRepository.save(legacyRoot);
        staleRoot = userRepository.save(staleRoot);
        configuredUser = userRepository.save(configuredUser);

        MountEntity mount = new MountEntity();
        mount.setId(UUID.randomUUID());
        mount.setOwnerId(legacyRoot.getId());
        mount.setType("local");
        mount.setName("legacy-mount");
        mount.setVirtualPath("./personal/legacy");
        mount.setPhysicalRoot("D:/tmp/legacy");
        mount.setState("created");
        mount.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        mount.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        mountRepository.save(mount);

        AuthSessionEntity session = new AuthSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(legacyRoot.getId());
        session.setUsername(legacyRoot.getUsername());
        session.setRefreshHash("hash_legacy");
        session.setStatus("active");
        session.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        session.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        authSessionRepository.save(session);

        ShareRoleEntity shareRole = new ShareRoleEntity();
        shareRole.setId(UUID.randomUUID());
        shareRole.setMountId(mount.getId());
        shareRole.setCreatorUserId(staleRoot.getId());
        shareRole.setName("legacy-role");
        shareRole.setSystem(false);
        shareRole.setState("active");
        shareRole.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        shareRole.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        shareRoleRepository.save(shareRole);

        ShareLinkEntity shareLink = new ShareLinkEntity();
        shareLink.setId(UUID.randomUUID());
        shareLink.setMountId(mount.getId());
        shareLink.setRoleId(shareRole.getId());
        shareLink.setToken("legacy_token");
        shareLink.setState("active");
        shareLink.setUsedCount(0);
        shareLink.setCreatedByUserId(legacyRoot.getId());
        shareLink.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        shareLink.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        shareLinkRepository.save(shareLink);

        SharedMountAccessEntity access = new SharedMountAccessEntity();
        access.setId(UUID.randomUUID());
        access.setUserId(staleRoot.getId());
        access.setMountId(mount.getId());
        access.setRoleId(shareRole.getId());
        access.setActive(true);
        access.setGrantedByLinkId(shareLink.getId());
        access.setGrantedAt(OffsetDateTime.now(ZoneOffset.UTC));
        access.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        sharedMountAccessRepository.save(access);

        ReflectionTestUtils.setField(rootUserBootstrap, "rootUsername", "cfg_root");
        ReflectionTestUtils.setField(rootUserBootstrap, "rootPassword", "CfgRoot@123");
        rootUserBootstrap.run();

        UserEntity primary = userRepository.findByUsername("cfg_root").orElseThrow();
        assertThat(primary.getPlatformRole()).isEqualTo(PlatformRole.ROOT);
        assertThat(primary.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(primary.getQosProfile()).isEqualTo("default");

        assertThat(userRepository.countByPlatformRole(PlatformRole.ROOT)).isEqualTo(1);
        assertThat(userRepository.findByUsername("legacy_root").orElseThrow().getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(userRepository.findByUsername("stale_root").orElseThrow().getStatus()).isEqualTo(UserStatus.DISABLED);

        assertThat(mountRepository.findById(mount.getId()).orElseThrow().getOwnerId()).isEqualTo(primary.getId());
        assertThat(authSessionRepository.findById(session.getId()).orElseThrow().getUserId()).isEqualTo(primary.getId());
        assertThat(shareRoleRepository.findById(shareRole.getId()).orElseThrow().getCreatorUserId()).isEqualTo(primary.getId());
        assertThat(shareLinkRepository.findById(shareLink.getId()).orElseThrow().getCreatedByUserId()).isEqualTo(primary.getId());
        assertThat(sharedMountAccessRepository.findById(access.getId()).orElseThrow().getUserId()).isEqualTo(primary.getId());
    }

    private UserEntity createUser(String username, PlatformRole role, UserStatus status, OffsetDateTime updatedAt) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPasswordHash("pwd_hash");
        user.setDisplayName(username);
        user.setPlatformRole(role);
        user.setStatus(status);
        user.setPreferredLanguage("zh-CN");
        user.setFileViewMode("list");
        user.setQosProfile("default");
        user.setCredentialVersion(1);
        user.setCredentialUpdatedAt(updatedAt);
        user.setCreatedAt(updatedAt.minusHours(1));
        user.setUpdatedAt(updatedAt);
        return user;
    }
}
