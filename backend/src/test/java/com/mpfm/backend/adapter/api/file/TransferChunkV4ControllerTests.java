package com.mpfm.backend.adapter.api.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mpfm.backend.application.file.FileApplicationService;
import com.mpfm.backend.application.file.NamespaceResolver;
import com.mpfm.backend.application.driver.base.DriverFactory;
import com.mpfm.backend.application.driver.sftp.SftpDriverUtil;
import com.mpfm.backend.application.driver.base.StorageDriver;
import com.mpfm.backend.application.monitor.TransferTelemetryService;
import com.mpfm.backend.application.monitor.UserTransferGovernanceService;
import com.mpfm.backend.application.security.TransferBandwidthLimiter;
import com.mpfm.backend.application.task.AsyncTaskStatus;
import com.mpfm.backend.application.transfer.TransferChunkService;
import com.mpfm.backend.application.transfer.TransferDirectUploadService;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@ExtendWith(MockitoExtension.class)
class TransferChunkV4ControllerTests {

    @Mock
    private TransferChunkService transferChunkService;
    @Mock
    private TransferDirectUploadService transferDirectUploadService;
    @Mock
    private FileApplicationService fileApplicationService;
    @Mock
    private NamespaceResolver namespaceResolver;
    @Mock
    private DriverFactory driverFactory;
    @Mock
    private StorageDriver storageDriver;
    @Mock
    private UserTransferGovernanceService userTransferGovernanceService;
    @Mock
    private TransferTelemetryService transferTelemetryService;
    @Mock
    private TransferBandwidthLimiter transferBandwidthLimiter;
    @Mock
    private DownloadResponseSupport downloadResponseSupport;
    @Mock
    private MountEntity mount;

    @Test
    void downloadProxyShouldDelegateToDownloadSupport() throws Exception {
        TransferChunkV4Controller controller = newController();
        Principal principal = () -> "alice";
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(mount.getType()).thenReturn("webdav");
        when(namespaceResolver.resolve("alice", "/personal/mount-a/demo.mp4", false, true))
                .thenReturn(new NamespaceResolver.ResolveResult(mount, "demo.mp4", "/personal/mount-a/demo.mp4", false, true));
        when(fileApplicationService.stat("alice", "/personal/mount-a/demo.mp4"))
                .thenReturn(new FileApplicationService.EntryResult(
                        "/personal/mount-a/demo.mp4", "demo.mp4", "file", 5L, "2026-05-28T00:00:00Z",
                        null, true, true, true, "\"etag\"", "v1"));
        ResponseEntity<StreamingResponseBody> delegated = ResponseEntity.ok(outputStream -> outputStream.write(new byte[] {1, 2, 3}));
        when(downloadResponseSupport.download(
                eq("/api/v4/transfers/downloads/proxy"),
                eq("alice"),
                any(),
                eq("\"etag\""),
                eq("2026-05-28T00:00:00Z"),
                any(),
                any())).thenReturn(delegated);

        var response = controller.downloadProxy("/personal/mount-a/demo.mp4", request, principal);
        assertThat(response).isSameAs(delegated);
    }

    @Test
    void streamUploadShouldUseDriverWriteForRemoteMount() throws Exception {
        TransferChunkV4Controller controller = newController();
        Principal principal = () -> "alice";
        byte[] payload = "hello-sftp".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(payload);
        request.setContentType("application/octet-stream");
        when(mount.getType()).thenReturn("webdav");
        when(namespaceResolver.resolve("alice", "/personal/mount-a", true, true))
                .thenReturn(new NamespaceResolver.ResolveResult(mount, ".", "/personal/mount-a", false, true));
        when(driverFactory.resolve("webdav")).thenReturn(storageDriver);
        when(storageDriver.put(any(), any())).thenReturn(null);

        TransferChunkV4Controller.UploadSessionResponse response = controller.createRuntimeUploadTaskByStream(
                "/personal/mount-a",
                "demo.txt",
                (long) payload.length,
                request,
                principal
        );

        verify(storageDriver).put(any(), any());
        verify(transferChunkService, never()).createStreamUpload(any(), any(), any(), any(Long.class), any());
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.taskId()).isEmpty();
        assertThat(response.uploadMode()).isEqualTo("stream");
        assertThat(response.totalChunks()).isEqualTo(1);
        assertThat(response.completedParts()).isEqualTo(List.of(1));
    }

    @Test
    void streamUploadShouldKeepLocalFlowForLocalMount() throws Exception {
        TransferChunkV4Controller controller = newController();
        Principal principal = () -> "alice";
        byte[] payload = "hello-local".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(payload);
        request.setContentType("application/octet-stream");
        when(mount.getType()).thenReturn("local");
        when(namespaceResolver.resolve("alice", "/personal/mount-local", true, true))
                .thenReturn(new NamespaceResolver.ResolveResult(mount, ".", "/personal/mount-local", false, true));
        TransferChunkService.UploadSession session = new TransferChunkService.UploadSession(
                "u1", "alice", "/personal/mount-local", "demo.txt",
                payload.length, payload.length, 1, "t1", AsyncTaskStatus.SUCCESS.name(),
                "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", "", List.of(1), "", "runtime", ""
        );
        when(transferChunkService.createStreamUpload(eq("alice"), eq("/personal/mount-local"), eq("demo.txt"), eq((long) payload.length), any()))
                .thenReturn(session);

        TransferChunkV4Controller.UploadSessionResponse response = controller.createRuntimeUploadTaskByStream(
                "/personal/mount-local",
                "demo.txt",
                (long) payload.length,
                request,
                principal
        );

        verify(transferChunkService).createStreamUpload(eq("alice"), eq("/personal/mount-local"), eq("demo.txt"), eq((long) payload.length), any());
        verify(fileApplicationService, never()).writeFileBytes(any(), any(), any(), any());
        assertThat(response.uploadId()).isEqualTo("u1");
    }

    private TransferChunkV4Controller newController() {
        return new TransferChunkV4Controller(
                transferChunkService,
                transferDirectUploadService,
                fileApplicationService,
                driverFactory,
                namespaceResolver,
                downloadResponseSupport,
                userTransferGovernanceService,
                transferTelemetryService,
                transferBandwidthLimiter
        );
    }
}
