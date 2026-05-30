package com.mpfm.backend.application.mount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mpfm.backend.application.driver.base.DriverCapability;
import com.mpfm.backend.application.driver.base.DriverFactory;
import com.mpfm.backend.application.driver.base.StorageDriver;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import com.mpfm.backend.infrastructure.persistence.repository.MountRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MountInspectServiceTests {

    @Test
    void shouldBuildCapabilitiesFromDriverCapability() {
        MountRepository mountRepository = mock(MountRepository.class);
        MountLifecycleService lifecycleService = mock(MountLifecycleService.class);
        MountQuotaService mountQuotaService = mock(MountQuotaService.class);
        StorageDriver sftpDriver = mock(StorageDriver.class);
        when(sftpDriver.protocol()).thenReturn("sftp");
        when(sftpDriver.capability()).thenReturn(new DriverCapability(true, true, true, true, true, true, true, false, true, false));
        DriverFactory driverFactory = new DriverFactory(List.of(sftpDriver));
        MountInspectService inspectService = new MountInspectService(mountRepository, lifecycleService, mountQuotaService, driverFactory);

        MountEntity mount = new MountEntity();
        mount.setId(UUID.randomUUID());
        mount.setType("sftp");
        when(lifecycleService.requireOwnedOrAdminMount("a123", mount.getId())).thenReturn(mount);

        MountApplicationService.MountCapabilities result = inspectService.capabilities("a123", mount.getId());

        assertThat(result.core()).containsEntry("upload", true).containsEntry("download", true);
        assertThat(result.extended()).containsEntry("copy", false).containsEntry("put_url", false);
        assertThat(result.constraints())
                .containsEntry("no_upload", false)
                .containsEntry("only_proxy", true)
                .containsEntry("no_link_url", true)
                .containsEntry("prefer_proxy", false);
    }

    @Test
    void shouldExposeWebdavPreferProxyProfile() {
        MountRepository mountRepository = mock(MountRepository.class);
        MountLifecycleService lifecycleService = mock(MountLifecycleService.class);
        MountQuotaService mountQuotaService = mock(MountQuotaService.class);
        StorageDriver webdavDriver = mock(StorageDriver.class);
        when(webdavDriver.protocol()).thenReturn("webdav");
        when(webdavDriver.capability()).thenReturn(DriverCapability.full().withDirectUpload(false));
        DriverFactory driverFactory = new DriverFactory(List.of(webdavDriver));
        MountInspectService inspectService = new MountInspectService(mountRepository, lifecycleService, mountQuotaService, driverFactory);

        MountEntity mount = new MountEntity();
        mount.setId(UUID.randomUUID());
        mount.setType("webdav");
        when(lifecycleService.requireOwnedOrAdminMount("a123", mount.getId())).thenReturn(mount);

        MountApplicationService.MountCapabilities result = inspectService.capabilities("a123", mount.getId());

        assertThat(result.constraints())
                .containsEntry("only_proxy", false)
                .containsEntry("no_link_url", false)
                .containsEntry("prefer_proxy", true);
    }
}
