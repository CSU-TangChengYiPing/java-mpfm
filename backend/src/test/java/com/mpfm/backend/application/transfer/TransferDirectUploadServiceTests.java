package com.mpfm.backend.application.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.mpfm.backend.application.driver.base.DriverFactory;
import com.mpfm.backend.application.driver.base.DriverCapability;
import com.mpfm.backend.application.driver.base.StorageDriver;
import com.mpfm.backend.application.file.NamespaceResolver;
import com.mpfm.backend.application.monitor.TransferTelemetryService;
import com.mpfm.backend.application.monitor.UserTransferGovernanceService;
import com.mpfm.backend.application.task.AsyncTaskService;
import com.mpfm.backend.application.task.TransferTaskRuntime;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferDirectUploadServiceTests {

    @Test
    void shouldReportUploadCapabilityByPutSupport() {
        NamespaceResolver namespaceResolver = mock(NamespaceResolver.class);
        DriverFactory driverFactory = mock(DriverFactory.class);
        UserTransferGovernanceService governanceService = mock(UserTransferGovernanceService.class);
        TransferTaskRuntime transferTaskRuntime = mock(TransferTaskRuntime.class);
        TransferSessionStore sessionStore = mock(TransferSessionStore.class);
        AsyncTaskService asyncTaskService = mock(AsyncTaskService.class);
        TransferTelemetryService transferTelemetryService = mock(TransferTelemetryService.class);
        StorageDriver storageDriver = mock(StorageDriver.class);

        when(namespaceResolver.resolve("a123", "/personal/11111111-1111-1111-1111-111111111111", true, true))
                .thenReturn(new NamespaceResolver.ResolveResult(mount("webdav"), ".", "/personal/11111111-1111-1111-1111-111111111111", false, true));
        when(driverFactory.resolve("webdav")).thenReturn(storageDriver);
        when(storageDriver.capability()).thenReturn(new DriverCapability(true, true, true, false, true, true, true, true, true, false));

        TransferDirectUploadService service = new TransferDirectUploadService(
                namespaceResolver, driverFactory, governanceService, transferTelemetryService,
                transferTaskRuntime, sessionStore, asyncTaskService, new ObjectMapper());

        TransferDirectUploadService.UploadCapability capability =
                service.getUploadCapability("a123", "/personal/11111111-1111-1111-1111-111111111111");

        assertThat(capability.supportsDirectUpload()).isFalse();
        assertThat(capability.provider()).isEqualTo("webdav");
        assertThat(capability.maxPartSizeBytes()).isZero();
        assertThat(capability.suggestedChunkSizeBytes()).isZero();
    }

    @Test
    void shouldReportUploadSupportedForLocalRuntimeStream() {
        NamespaceResolver namespaceResolver = mock(NamespaceResolver.class);
        DriverFactory driverFactory = mock(DriverFactory.class);
        UserTransferGovernanceService governanceService = mock(UserTransferGovernanceService.class);
        TransferTaskRuntime transferTaskRuntime = mock(TransferTaskRuntime.class);
        TransferSessionStore sessionStore = mock(TransferSessionStore.class);
        AsyncTaskService asyncTaskService = mock(AsyncTaskService.class);
        TransferTelemetryService transferTelemetryService = mock(TransferTelemetryService.class);
        StorageDriver storageDriver = mock(StorageDriver.class);

        when(namespaceResolver.resolve("a123", "/personal/11111111-1111-1111-1111-111111111111", true, true))
                .thenReturn(new NamespaceResolver.ResolveResult(mount("local"), ".", "/personal/11111111-1111-1111-1111-111111111111", false, true));
        when(driverFactory.resolve("local")).thenReturn(storageDriver);
        when(storageDriver.capability()).thenReturn(DriverCapability.full().withDirectUpload(false));

        TransferDirectUploadService service = new TransferDirectUploadService(
                namespaceResolver, driverFactory, governanceService, transferTelemetryService,
                transferTaskRuntime, sessionStore, asyncTaskService, new ObjectMapper());

        TransferDirectUploadService.UploadCapability capability =
                service.getUploadCapability("a123", "/personal/11111111-1111-1111-1111-111111111111");

        assertThat(capability.supportsDirectUpload()).isTrue();
        assertThat(capability.provider()).isEqualTo("local");
    }

    @Test
    void shouldRejectDirectTicketWhenDriverNotSupported() {
        NamespaceResolver namespaceResolver = mock(NamespaceResolver.class);
        DriverFactory driverFactory = mock(DriverFactory.class);
        UserTransferGovernanceService governanceService = mock(UserTransferGovernanceService.class);
        TransferTaskRuntime transferTaskRuntime = mock(TransferTaskRuntime.class);
        TransferSessionStore sessionStore = mock(TransferSessionStore.class);
        AsyncTaskService asyncTaskService = mock(AsyncTaskService.class);
        TransferTelemetryService transferTelemetryService = mock(TransferTelemetryService.class);
        StorageDriver storageDriver = mock(StorageDriver.class);

        when(namespaceResolver.resolve("a123", "/personal/11111111-1111-1111-1111-111111111111", true, true))
                .thenReturn(new NamespaceResolver.ResolveResult(mount("sftp"), ".", "/personal/11111111-1111-1111-1111-111111111111", false, true));
        when(driverFactory.resolve("sftp")).thenReturn(storageDriver);
        when(storageDriver.capability()).thenReturn(DriverCapability.full().withDirectUpload(false));

        TransferDirectUploadService service = new TransferDirectUploadService(
                namespaceResolver, driverFactory, governanceService, transferTelemetryService,
                transferTaskRuntime, sessionStore, asyncTaskService, new ObjectMapper());

        assertThatThrownBy(() -> service.createDirectUploadTicket(
                "a123",
                "/personal/11111111-1111-1111-1111-111111111111",
                "a.bin",
                1024L,
                1024L))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getCode() == ErrorCode.CAPABILITY_NOT_SUPPORTED);

        verify(sessionStore, never()).saveUpload(any());
    }

    @Test
    void shouldOnlyPersistDirectUploadSessionWhenAckCompleted() {
        NamespaceResolver namespaceResolver = mock(NamespaceResolver.class);
        DriverFactory driverFactory = mock(DriverFactory.class);
        UserTransferGovernanceService governanceService = mock(UserTransferGovernanceService.class);
        TransferTaskRuntime transferTaskRuntime = mock(TransferTaskRuntime.class);
        TransferSessionStore sessionStore = mock(TransferSessionStore.class);
        AsyncTaskService asyncTaskService = mock(AsyncTaskService.class);
        TransferTelemetryService transferTelemetryService = mock(TransferTelemetryService.class);

        UUID taskId = UUID.randomUUID();
        TransferChunkService.UploadSession session = new TransferChunkService.UploadSession(
                UUID.randomUUID().toString(), "a123", "/personal/11111111-1111-1111-1111-111111111111", "a.bin",
                10L, 5L, 2, taskId.toString(), "RUNNING",
                Instant.now().toString(), Instant.now().toString(), "", new ArrayList<>(), "", "direct", "provider-1");
        when(sessionStore.findUploadByTaskId(taskId)).thenReturn(Optional.of(session));
        when(sessionStore.loadUpload(session.uploadId())).thenReturn(session);

        TransferDirectUploadService service = new TransferDirectUploadService(
                namespaceResolver, driverFactory, governanceService, transferTelemetryService,
                transferTaskRuntime, sessionStore, asyncTaskService, new ObjectMapper());

        TransferChunkService.UploadSession updated = service.ackDirectUpload(
                "a123",
                taskId.toString(),
                new TransferDirectUploadService.DirectUploadAckRequest(2, true, false, "")
        );

        assertThat(updated.status()).isEqualTo("RUNNING");
        assertThat(updated.completedParts()).contains(2);
        verify(asyncTaskService, never()).markSuccess(taskId);
        verify(asyncTaskService).updatePayloadJson(any(UUID.class), anyString());
    }

    @Test
    void shouldRejectDirectTicketWhenWebdavDirectDisabled() {
        NamespaceResolver namespaceResolver = mock(NamespaceResolver.class);
        DriverFactory driverFactory = mock(DriverFactory.class);
        UserTransferGovernanceService governanceService = mock(UserTransferGovernanceService.class);
        TransferTaskRuntime transferTaskRuntime = mock(TransferTaskRuntime.class);
        TransferSessionStore sessionStore = mock(TransferSessionStore.class);
        AsyncTaskService asyncTaskService = mock(AsyncTaskService.class);
        TransferTelemetryService transferTelemetryService = mock(TransferTelemetryService.class);
        StorageDriver storageDriver = mock(StorageDriver.class);
        UUID taskId = UUID.randomUUID();

        when(namespaceResolver.resolve("a123", "/personal/11111111-1111-1111-1111-111111111111", true, true))
                .thenReturn(new NamespaceResolver.ResolveResult(mount("webdav"), ".", "/personal/11111111-1111-1111-1111-111111111111", false, true));
        when(driverFactory.resolve("webdav")).thenReturn(storageDriver);
        when(storageDriver.capability()).thenReturn(DriverCapability.full().withDirectUpload(false));

        TransferDirectUploadService service = new TransferDirectUploadService(
                namespaceResolver, driverFactory, governanceService, transferTelemetryService,
                transferTaskRuntime, sessionStore, asyncTaskService, new ObjectMapper());

        assertThatThrownBy(() -> service.createDirectUploadTicket(
                "a123", "/personal/11111111-1111-1111-1111-111111111111", "a.bin", 10L, 5L))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getCode() == ErrorCode.CAPABILITY_NOT_SUPPORTED);
    }

    @Test
    void shouldRejectCreateDirectTicketWhenDirectDisabled() {
        NamespaceResolver namespaceResolver = mock(NamespaceResolver.class);
        DriverFactory driverFactory = mock(DriverFactory.class);
        UserTransferGovernanceService governanceService = mock(UserTransferGovernanceService.class);
        TransferTaskRuntime transferTaskRuntime = mock(TransferTaskRuntime.class);
        TransferSessionStore sessionStore = mock(TransferSessionStore.class);
        AsyncTaskService asyncTaskService = mock(AsyncTaskService.class);
        TransferTelemetryService transferTelemetryService = mock(TransferTelemetryService.class);
        StorageDriver storageDriver = mock(StorageDriver.class);
        UUID taskId = UUID.randomUUID();

        when(namespaceResolver.resolve("a123", "/personal/11111111-1111-1111-1111-111111111111", true, true))
                .thenReturn(new NamespaceResolver.ResolveResult(mount("webdav"), ".", "/personal/11111111-1111-1111-1111-111111111111", false, true));
        when(driverFactory.resolve("webdav")).thenReturn(storageDriver);
        when(storageDriver.capability()).thenReturn(DriverCapability.full().withDirectUpload(false));

        TransferDirectUploadService service = new TransferDirectUploadService(
                namespaceResolver, driverFactory, governanceService, transferTelemetryService,
                transferTaskRuntime, sessionStore, asyncTaskService, new ObjectMapper());

        assertThatThrownBy(() -> service.createDirectUploadTicket(
                "a123", "/personal/11111111-1111-1111-1111-111111111111", "a.bin", 10L, 5L))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getCode() == ErrorCode.CAPABILITY_NOT_SUPPORTED);
    }

    private MountEntity mount(String type) {
        MountEntity entity = new MountEntity();
        entity.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        entity.setName("m1");
        entity.setType(type);
        entity.setState("enabled");
        entity.setPhysicalRoot("D:/tmp");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        return entity;
    }
}
