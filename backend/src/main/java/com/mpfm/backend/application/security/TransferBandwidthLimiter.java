package com.mpfm.backend.application.security;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 传输带宽预算判定器：仅做预算判定，不在请求线程内执行阻塞等待。
 */
@Service
public class TransferBandwidthLimiter {
    private static final long ZERO_NANOS = 0L;
    private static final long LIVE_WINDOW_NANOS = 5L * 1_000_000_000L;
    private static final String DIRECTION_UPLOAD = "upload";
    private static final String DIRECTION_DOWNLOAD = "download";

    private final QosPolicyService qosPolicyService;
    private final Map<String, ShaperState> shaperStates = new ConcurrentHashMap<>();
    private final Map<String, Deque<TransferSample>> liveUploadSamples = new ConcurrentHashMap<>();
    private final Map<String, Deque<TransferSample>> liveDownloadSamples = new ConcurrentHashMap<>();
    private final NanoClock nanoClock;
    private final NanoSleeper nanoSleeper;

    @Autowired
    public TransferBandwidthLimiter(QosPolicyService qosPolicyService) {
        this(qosPolicyService, System::nanoTime, LockSupport::parkNanos);
    }

    TransferBandwidthLimiter(QosPolicyService qosPolicyService, NanoClock nanoClock) {
        this(qosPolicyService, nanoClock, LockSupport::parkNanos);
    }

    TransferBandwidthLimiter(QosPolicyService qosPolicyService, NanoClock nanoClock, NanoSleeper nanoSleeper) {
        this.qosPolicyService = qosPolicyService;
        this.nanoClock = nanoClock;
        this.nanoSleeper = nanoSleeper;
    }

    public void checkUpload(String username, long bytes) {
        check(username, DIRECTION_UPLOAD, bytes);
    }

    public void checkDownload(String username, long bytes) {
        check(username, DIRECTION_DOWNLOAD, bytes);
    }

    /**
     * 在读取请求体字节时等待令牌，不抛出“超限失败”。
     */
    public void awaitUploadPermit(String username, long bytes) {
        await(username, DIRECTION_UPLOAD, bytes);
    }

    /**
     * 在读取响应体字节时等待令牌，不抛出“超限失败”。
     */
    public void awaitDownloadPermit(String username, long bytes) {
        await(username, DIRECTION_DOWNLOAD, bytes);
    }

    private void check(String username, String direction, long bytes) {
        acquire(username, direction, bytes, true, false);
    }

    private void await(String username, String direction, long bytes) {
        acquire(username, direction, bytes, false, true);
    }

    private void acquire(String username, String direction, long bytes, boolean failFast, boolean recordTelemetry) {
        if (bytes <= 0 || username == null || username.isBlank()) {
            return;
        }
        QosPolicyService.QosPolicy policy = qosPolicyService.effectivePolicy(username);
        long limit = DIRECTION_UPLOAD.equals(direction) ? policy.maxUploadBps() : policy.maxDownloadBps();
        if (limit <= 0) {
            return;
        }
        String key = username + "::" + direction;
        ShaperState state = shaperStates.computeIfAbsent(key, ignored -> new ShaperState(0L));
        synchronized (state) {
            long now = nanoClock.now();
            long remainingBytes = bytes;
            while (remainingBytes > 0) {
                long currentSlotBytes = remainingBytes;
                long slotNanos = Math.max(1L, (currentSlotBytes * 1_000_000_000L) / limit);
                long startAt = Math.max(now, state.nextAvailableAtNanos);
                long waitNanos = Math.max(0L, startAt - now);
                if (waitNanos > ZERO_NANOS) {
                    if (failFast) {
                        throw new BusinessException(ErrorCode.CAPABILITY_RESTRICTED, "bandwidth budget exceeded");
                    }
                    nanoSleeper.parkNanos(waitNanos);
                    now = nanoClock.now();
                    continue;
                }
                state.nextAvailableAtNanos = startAt + slotNanos;
                remainingBytes = 0L;
            }
            if (recordTelemetry) {
                recordSample(username, direction, bytes, now);
            }
        }
    }

    /** 获取用户最近窗口内上传带宽（bps）。 */
    public long currentUploadBps(String username) {
        return currentBps(liveUploadSamples, username);
    }

    /** 获取用户最近窗口内下载带宽（bps）。 */
    public long currentDownloadBps(String username) {
        return currentBps(liveDownloadSamples, username);
    }

    /** 获取最近窗口内有上传流量样本的用户。 */
    public Set<String> observedUploadUsers() {
        return observedUsers(liveUploadSamples);
    }

    /** 获取最近窗口内有下载流量样本的用户。 */
    public Set<String> observedDownloadUsers() {
        return observedUsers(liveDownloadSamples);
    }

    private void recordSample(String username, String direction, long bytes, long nowNanos) {
        Map<String, Deque<TransferSample>> source = DIRECTION_UPLOAD.equals(direction) ? liveUploadSamples : liveDownloadSamples;
        Deque<TransferSample> queue = source.computeIfAbsent(username, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(new TransferSample(nowNanos, bytes));
            purgeExpired(queue, nowNanos);
        }
    }

    private long currentBps(Map<String, Deque<TransferSample>> source, String username) {
        if (username == null || username.isBlank()) {
            return 0L;
        }
        Deque<TransferSample> queue = source.get(username);
        if (queue == null) {
            return 0L;
        }
        synchronized (queue) {
            long now = nanoClock.now();
            purgeExpired(queue, now);
            long bytes = queue.stream().mapToLong(TransferSample::bytes).sum();
            if (bytes <= 0) {
                return 0L;
            }
            return Math.max(1L, bytes / 5L);
        }
    }

    private Set<String> observedUsers(Map<String, Deque<TransferSample>> source) {
        Set<String> users = new HashSet<>();
        long now = nanoClock.now();
        for (Map.Entry<String, Deque<TransferSample>> entry : source.entrySet()) {
            Deque<TransferSample> queue = entry.getValue();
            synchronized (queue) {
                purgeExpired(queue, now);
                if (!queue.isEmpty()) {
                    users.add(entry.getKey());
                }
            }
        }
        return users;
    }

    private void purgeExpired(Deque<TransferSample> queue, long nowNanos) {
        long threshold = nowNanos - LIVE_WINDOW_NANOS;
        while (!queue.isEmpty() && queue.peekFirst().timestampNanos() < threshold) {
            queue.pollFirst();
        }
    }

    private static final class ShaperState {
        private long nextAvailableAtNanos;

        private ShaperState(long nextAvailableAtNanos) {
            this.nextAvailableAtNanos = nextAvailableAtNanos;
        }
    }

    @FunctionalInterface
    interface NanoClock {
        long now();
    }

    @FunctionalInterface
    interface NanoSleeper {
        void parkNanos(long nanos);
    }

    private record TransferSample(long timestampNanos, long bytes) { }
}

