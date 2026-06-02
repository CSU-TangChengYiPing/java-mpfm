package com.mpfm.backend.application.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.mpfm.backend.application.share.v5.ShareAuthorizationV5Service;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.entity.UserEntity;
import com.mpfm.backend.infrastructure.persistence.repository.MountRepository;
import com.mpfm.backend.infrastructure.persistence.repository.UserRepository;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class FileAccessServiceTests {

    @TempDir
    Path tempDir;

    private MountRepository mountRepository;
    private ShareAuthorizationV5Service shareAuthorizationV5Service;
    private UserRepository userRepository;
    private FileAccessService fileAccessService;
    private MountEntity mount;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        mountRepository = Mockito.mock(MountRepository.class);
        shareAuthorizationV5Service = Mockito.mock(ShareAuthorizationV5Service.class);
        userRepository = Mockito.mock(UserRepository.class);
        fileAccessService = new FileAccessService(shareAuthorizationV5Service, mountRepository, userRepository);
        mount = new MountEntity();
        mount.setId(UUID.randomUUID());
        mount.setName("m-demo");
        mount.setType("local");
        mount.setPhysicalRoot(tempDir.toString());
        mount.setState("enabled");
        user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        mount.setOwnerId(user.getId());
        given(mountRepository.findById(mount.getId())).willReturn(Optional.of(mount));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(shareAuthorizationV5Service.effective(any(), eq(mount.getId()), any()))
                .willReturn(new ShareAuthorizationV5Service.EffectivePermissionResult(".", true, true, true, java.util.List.of(), "role_union"));
    }

    @Test
    void requireAccessShouldPreserveNestedRelativePathForWrite() {
        FileAccessService.AccessContext context = fileAccessService.requireAccess("alice", mount.getId(), "docs/subdir/new.txt", true, true);

        assertThat(context.target()).isEqualTo(tempDir.resolve("docs/subdir/new.txt"));
        assertThat(context.virtualPath()).isEqualTo("./personal/m-demo/docs/subdir/new.txt");
        assertThat(context.ownerOrAdmin()).isTrue();
    }
}
