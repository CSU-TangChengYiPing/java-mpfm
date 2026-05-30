package com.mpfm.backend.application.file;

import com.mpfm.backend.application.share.v5.ShareAuthorizationV5Service;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.repository.MountRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

class NamespaceResolverTests {

    private MountRepository mountRepository;
    private ShareAuthorizationV5Service shareAuthorizationV5Service;
    private UserRepository userRepository;
    private NamespaceResolver resolver;
    private UUID mountId;
    private MountEntity mount;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        mountRepository = Mockito.mock(MountRepository.class);
        shareAuthorizationV5Service = Mockito.mock(ShareAuthorizationV5Service.class);
        userRepository = Mockito.mock(UserRepository.class);
        resolver = new NamespaceResolver(mountRepository, shareAuthorizationV5Service, userRepository);
        mountId = UUID.randomUUID();
        mount = new MountEntity();
        mount.setId(mountId);
        mount.setName("m-demo");
        mount.setType("local");
        mount.setPhysicalRoot("D:/data");
        mount.setState("enabled");
        user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        given(mountRepository.findById(mountId)).willReturn(Optional.of(mount));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(mountRepository.findByOwnerIdAndNameAndStateNot(user.getId(), "m-demo", "soft_deleted")).willReturn(Optional.of(mount));
        given(mountRepository.findByNameAndStateNot("m-demo", "soft_deleted")).willReturn(Optional.of(mount));
        given(shareAuthorizationV5Service.effective(any(), eq(mountId), any()))
                .willReturn(new ShareAuthorizationV5Service.EffectivePermissionResult(".", true, true, true, java.util.List.of(), "role_union"));
    }

    @Test
    void shouldResolvePersonalVirtualPathToMountAndRelPath() {
        NamespaceResolver.ResolveResult result =
                resolver.resolve("alice", "/personal/m-demo/docs/a.txt", false, true);

        assertThat(result.mount().getId()).isEqualTo(mountId);
        assertThat(result.relPath()).isEqualTo("docs/a.txt");
        assertThat(result.shared()).isFalse();
    }

    @Test
    void shouldResolveSharedVirtualPathToMountAndRelPath() {
        NamespaceResolver.ResolveResult result =
                resolver.resolve("alice", "/shared/m-demo/team/readme.md", false, true);

        assertThat(result.mount().getId()).isEqualTo(mountId);
        assertThat(result.relPath()).isEqualTo("team/readme.md");
        assertThat(result.shared()).isTrue();
    }

    @Test
    void shouldRejectInvalidPrefix() {
        assertThatThrownBy(() -> resolver.resolve("alice", "/unknown/" + mountId + "/a.txt", false, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void shouldRejectMissingMountByName() {
        assertThatThrownBy(() -> resolver.resolve("alice", "/personal/missing/a.txt", false, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void shouldRejectNotVisibleSharedPath() {
        given(shareAuthorizationV5Service.effective(any(), eq(mountId), any()))
                .willReturn(new ShareAuthorizationV5Service.EffectivePermissionResult(".", false, false, false, java.util.List.of(UUID.randomUUID()), "role_union"));

        assertThatThrownBy(() -> resolver.resolve("alice", "/shared/m-demo/x.txt", false, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void shouldRejectNotWritableWhenWriteRequired() {
        given(shareAuthorizationV5Service.effective(any(), eq(mountId), any()))
                .willReturn(new ShareAuthorizationV5Service.EffectivePermissionResult(".", true, true, false, java.util.List.of(UUID.randomUUID()), "role_union"));

        assertThatThrownBy(() -> resolver.resolve("alice", "/shared/m-demo/x.txt", true, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
    }
}
