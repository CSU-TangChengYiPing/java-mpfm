package com.mpfm.backend.application.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.mpfm.backend.application.security.TransferBandwidthLimiter;
import com.mpfm.backend.infrastructure.persistence.entity.AsyncTaskEntity;
import com.mpfm.backend.infrastructure.persistence.repository.AsyncTaskRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferTelemetryServiceTests {

    @Mock
    private AsyncTaskRepository asyncTaskRepository;

    @Mock
    private TransferBandwidthLimiter transferBandwidthLimiter;

    @InjectMocks
    private TransferTelemetryService transferTelemetryService;

    @Test
    void shouldAggregateUploadAndDownloadSpeedForCurrentUser() {
        AsyncTaskEntity upload = task("alice", "upload_v2", 20L * 1024 * 1024, 10, 3);
        AsyncTaskEntity download = task("alice", "download_v2", 10L * 1024 * 1024, 8, 5);
        given(asyncTaskRepository.findByStatusIn(List.of("PENDING", "RUNNING")))
                .willReturn(List.of(upload, download));
        given(transferBandwidthLimiter.currentUploadBps("alice")).willReturn(3072L);

        TransferTelemetryService.TransferSnapshot snapshot = transferTelemetryService.forCurrentUser("alice");

        assertThat(snapshot.username()).isEqualTo("alice");
        assertThat(snapshot.uploadBps()).isEqualTo(3072L);
        assertThat(snapshot.downloadBps()).isEqualTo(0L);
        assertThat(snapshot.activeUploadTasks()).isEqualTo(1);
        assertThat(snapshot.activeDownloadTasks()).isEqualTo(0);
        assertThat(snapshot.activeChunks()).isEqualTo((10 - 3) + (8 - 5));
    }

    @Test
    void shouldSortAllUsersByTotalRateDesc() {
        AsyncTaskEntity alice = task("alice", "upload_v2", 30L * 1024 * 1024, 10, 4);
        AsyncTaskEntity bob = task("bob", "download_v2", 5L * 1024 * 1024, 10, 1);
        given(asyncTaskRepository.findByStatusIn(List.of("PENDING", "RUNNING")))
                .willReturn(List.of(bob, alice));
        given(transferBandwidthLimiter.observedUploadUsers()).willReturn(java.util.Set.of());
        given(transferBandwidthLimiter.currentUploadBps("alice")).willReturn(4096L);
        given(transferBandwidthLimiter.currentUploadBps("bob")).willReturn(256L);

        List<TransferTelemetryService.TransferSnapshot> snapshots = transferTelemetryService.forAllUsers();

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots).extracting(TransferTelemetryService.TransferSnapshot::username)
                .containsExactlyInAnyOrder("alice", "bob");
        assertThat(snapshots.get(0).totalBps()).isGreaterThanOrEqualTo(snapshots.get(1).totalBps());
    }

    @Test
    void shouldBuildUserTimelineWithinWindow() {
        AsyncTaskEntity old = task("alice", "upload_v2", 1024L, 1, 0);
        old.setUpdatedAt(OffsetDateTime.now().minusMinutes(20));
        AsyncTaskEntity currentUpload = task("alice", "upload_v2", 10L * 1024 * 1024, 8, 3);
        AsyncTaskEntity currentDownload = task("alice", "download_v2", 6L * 1024 * 1024, 6, 2);
        given(asyncTaskRepository.findByOperatorOrderByUpdatedAtDesc("alice"))
                .willReturn(List.of(old, currentUpload, currentDownload));

        List<TransferTelemetryService.TransferTimelinePoint> timeline = transferTelemetryService.userTimeline("alice", 5);

        assertThat(timeline).isNotEmpty();
        assertThat(timeline.get(0).uploadBps()).isGreaterThanOrEqualTo(0L);
        assertThat(timeline.get(0).downloadBps()).isGreaterThanOrEqualTo(0L);
    }

    private AsyncTaskEntity task(String user, String action, long bytes, int totalChunks, int completedChunks) {
        AsyncTaskEntity entity = new AsyncTaskEntity();
        entity.setId(UUID.randomUUID());
        entity.setOperator(user);
        entity.setAction(action);
        entity.setStatus("RUNNING");
        entity.setTransferredBytes(bytes);
        entity.setTotalChunks(totalChunks);
        entity.setCompletedChunks(completedChunks);
        entity.setCreatedAt(OffsetDateTime.now().minusSeconds(10));
        entity.setUpdatedAt(OffsetDateTime.now());
        return entity;
    }
}
