package com.mpfm.backend.application.monitor;

import com.mpfm.backend.application.task.TransferTaskAction;
import com.mpfm.backend.application.security.TransferBandwidthLimiter;
import com.mpfm.backend.infrastructure.persistence.entity.AsyncTaskEntity;
import com.mpfm.backend.infrastructure.persistence.repository.AsyncTaskRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 传输遥测服务，基于任务快照聚合用户上传/下载实时速率与活动数。
 */
@Service
public class TransferTelemetryService {
    private static final List<String> ACTIVE_STATUSES = List.of("PENDING", "RUNNING");
    private static final long ZERO_BYTES = 0L;

    private final AsyncTaskRepository asyncTaskRepository;
    private final TransferBandwidthLimiter transferBandwidthLimiter;
    private final Map<String, Deque<TransferEvent>> liveUploadEvents = new ConcurrentHashMap<>();
    private final Map<String, Deque<TransferEvent>> liveDownloadEvents = new ConcurrentHashMap<>();

    public TransferTelemetryService(AsyncTaskRepository asyncTaskRepository,
                                    TransferBandwidthLimiter transferBandwidthLimiter) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.transferBandwidthLimiter = transferBandwidthLimiter;
    }

    /** 记录上传实时流量，避免仅靠任务快照导致上传速率观察滞后。 */
    public void recordLiveUpload(String username, long bytes) {
        if (username == null || username.isBlank() || bytes <= 0) {
            return;
        }
        Deque<TransferEvent> queue = liveUploadEvents.computeIfAbsent(username, key -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(new TransferEvent(System.currentTimeMillis(), bytes));
            purgeExpired(queue, System.currentTimeMillis());
        }
    }

    public TransferSnapshot forCurrentUser(String username) {
        List<AsyncTaskEntity> activeTasks = asyncTaskRepository.findByStatusIn(ACTIVE_STATUSES).stream()
                .filter(task -> username.equals(task.getOperator()))
                .toList();
        return aggregateUserWithLiveUpload(username, activeTasks);
    }

    public List<TransferSnapshot> forAllUsers() {
        Map<String, List<AsyncTaskEntity>> byUser = asyncTaskRepository.findByStatusIn(ACTIVE_STATUSES).stream()
                .collect(Collectors.groupingBy(AsyncTaskEntity::getOperator));
        Set<String> usernames = new HashSet<>(byUser.keySet());
        Set<String> limiterUsers = transferBandwidthLimiter.observedUploadUsers();
        if (limiterUsers != null) {
            usernames.addAll(limiterUsers);
        }
        usernames.addAll(liveUploadEvents.keySet());
        usernames.addAll(liveDownloadEvents.keySet());
        return usernames.stream()
                .map(username -> aggregateUserWithLiveUpload(username, byUser.getOrDefault(username, List.of())))
                .sorted(Comparator.comparingLong(TransferSnapshot::totalBps).reversed())
                .toList();
    }

    public List<TransferTimelinePoint> userTimeline(String username, int minutes) {
        int safeMinutes = Math.max(1, Math.min(15, minutes));
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime threshold = now.minusMinutes(safeMinutes);
        List<AsyncTaskEntity> userTasks = asyncTaskRepository.findByOperatorOrderByUpdatedAtDesc(username).stream()
                .filter(task -> task.getUpdatedAt() != null && !task.getUpdatedAt().isBefore(threshold))
                .toList();
        Map<OffsetDateTime, List<AsyncTaskEntity>> grouped = userTasks.stream().collect(Collectors.groupingBy(task ->
                task.getUpdatedAt().truncatedTo(ChronoUnit.SECONDS)));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> aggregateTimelinePoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private TransferSnapshot aggregateUser(String username, List<AsyncTaskEntity> tasks) {
        long uploadBps = 0L;
        long downloadBps = 0L;
        int uploadTasks = 0;
        int downloadTasks = 0;
        int activeChunks = 0;
        for (AsyncTaskEntity task : tasks) {
            String action = safeLower(task.getAction());
            long speed = estimateSpeed(task);
            activeChunks += Math.max(0, task.getTotalChunks() - task.getCompletedChunks());
            if (isUploadAction(action)) {
                uploadTasks += 1;
                uploadBps += speed;
            } else if (isDownloadAction(action)) {
                downloadTasks += 1;
                downloadBps += speed;
            }
        }
        return new TransferSnapshot(username, uploadBps, downloadBps, uploadTasks, downloadTasks, activeChunks);
    }

    private TransferSnapshot aggregateUserWithLiveUpload(String username, List<AsyncTaskEntity> tasks) {
        TransferSnapshot base = aggregateUser(username, tasks);
        long limiterUpload = transferBandwidthLimiter.currentUploadBps(username);
        long liveUpload = liveUploadBps(username);
        long liveDownload = liveDownloadBps(username);
        return new TransferSnapshot(
                base.username(),
                Math.max(Math.max(0L, limiterUpload), liveUpload),
                Math.max(base.downloadBps(), liveDownload),
                base.activeUploadTasks(),
                base.activeDownloadTasks(),
                base.activeChunks()
        );
    }

    /** 记录下载实时流量，补齐 Range 分片下载不落 AsyncTask 的速率观测。 */
    public void recordLiveDownload(String username, long bytes) {
        if (username == null || username.isBlank() || bytes <= 0) {
            return;
        }
        Deque<TransferEvent> queue = liveDownloadEvents.computeIfAbsent(username, key -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(new TransferEvent(System.currentTimeMillis(), bytes));
            purgeExpired(queue, System.currentTimeMillis());
        }
    }

    private TransferTimelinePoint aggregateTimelinePoint(OffsetDateTime timestamp, List<AsyncTaskEntity> tasks) {
        long uploadBps = 0L;
        long downloadBps = 0L;
        for (AsyncTaskEntity task : tasks) {
            String action = safeLower(task.getAction());
            long speed = estimateSpeed(task);
            if (isUploadAction(action)) {
                uploadBps += speed;
            } else if (isDownloadAction(action)) {
                downloadBps += speed;
            }
        }
        return new TransferTimelinePoint(timestamp.toString(), uploadBps, downloadBps);
    }

    private boolean isUploadAction(String action) {
        return TransferTaskAction.isUploadAction(action);
    }

    private boolean isDownloadAction(String action) {
        return TransferTaskAction.isDownloadAction(action);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private long estimateSpeed(AsyncTaskEntity task) {
        long transferredBytes = Math.max(0L, task.getTransferredBytes());
        if (transferredBytes <= ZERO_BYTES) {
            return ZERO_BYTES;
        }
        OffsetDateTime createdAt = task.getCreatedAt();
        OffsetDateTime updatedAt = task.getUpdatedAt();
        if (createdAt == null || updatedAt == null) {
            return 0L;
        }
        long seconds = Math.max(1L, Duration.between(createdAt, updatedAt).getSeconds());
        return transferredBytes / seconds;
    }

    private long liveDownloadBps(String username) {
        return liveBpsOf(liveDownloadEvents, username);
    }

    private long liveUploadBps(String username) {
        return liveBpsOf(liveUploadEvents, username);
    }

    private long liveBpsOf(Map<String, Deque<TransferEvent>> source, String username) {
        Deque<TransferEvent> queue = source.get(username);
        if (queue == null) {
            return 0L;
        }
        synchronized (queue) {
            long now = System.currentTimeMillis();
            purgeExpired(queue, now);
            long bytes = queue.stream().mapToLong(TransferEvent::bytes).sum();
            if (bytes <= 0L) {
                return 0L;
            }
            return Math.max(1L, bytes / 5L);
        }
    }

    private void purgeExpired(Deque<TransferEvent> queue, long nowMs) {
        long threshold = nowMs - (5L * 1000L);
        while (!queue.isEmpty() && queue.peekFirst().timestampMs() < threshold) {
            queue.pollFirst();
        }
    }

    /** 用户传输快照，聚合上传/下载速率与活动并发。 */
    public record TransferSnapshot(String username, long uploadBps, long downloadBps,
                                   int activeUploadTasks, int activeDownloadTasks, int activeChunks) {
        public long totalBps() {
            return Math.max(0L, uploadBps) + Math.max(0L, downloadBps);
        }
    }

    /** 单用户传输时间线点，按秒聚合上传与下载估算速率。 */
    public record TransferTimelinePoint(String timestamp, long uploadBps, long downloadBps) { }

    /** 实时流量事件：用于补充 v2 分片上传速率观测。 */
    private record TransferEvent(long timestampMs, long bytes) { }
}
