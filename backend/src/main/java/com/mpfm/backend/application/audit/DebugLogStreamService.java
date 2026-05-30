package com.mpfm.backend.application.audit;

import com.mpfm.backend.common.audit.SecurityEventLogger;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * DEBUG 日志流服务，负责按筛选条件推送 SSE 日志事件与心跳事件。
 */
@Service
public class DebugLogStreamService {
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;
    private static final long POLL_INTERVAL_SECONDS = 1L;
    private static final AtomicLong EVENT_SEQ = new AtomicLong(1L);

    private final BackendLogReadService backendLogReadService;
    private final SecurityEventLogger securityEventLogger;
    private final DebugLogLineParser debugLogLineParser;
    private final DebugLogStreamRequestValidator debugLogStreamRequestValidator;
    private final ScheduledExecutorService scheduler;
    private final Map<String, StreamSession> sessions = new ConcurrentHashMap<>();

    public DebugLogStreamService(BackendLogReadService backendLogReadService,
                                 SecurityEventLogger securityEventLogger,
                                 DebugLogLineParser debugLogLineParser,
                                 DebugLogStreamRequestValidator debugLogStreamRequestValidator) {
        this.backendLogReadService = backendLogReadService;
        this.securityEventLogger = securityEventLogger;
        this.debugLogLineParser = debugLogLineParser;
        this.debugLogStreamRequestValidator = debugLogStreamRequestValidator;
        this.scheduler = Executors.newScheduledThreadPool(2, new DebugStreamThreadFactory());
    }

    /**
     * 创建日志 SSE 订阅。
     *
     * @param request 筛选参数。
     * @param actor   操作人。
     * @return SSE 发射器。
     */
    public SseEmitter subscribe(DebugLogStreamRequest request, String actor) {
        DebugLogStreamRequest resolvedRequest = debugLogStreamRequestValidator.validateAndNormalize(request);
        Path resolvedPath = backendLogReadService.resolveLogPath(resolvedRequest.file())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid log file"));
        if (!backendLogReadService.logExists(resolvedPath)) {
            throw new BusinessException(ErrorCode.DEBUG_STREAM_SOURCE_UNAVAILABLE, "log file not found");
        }
        SseEmitter emitter = new SseEmitter(0L);
        String sessionId = UUID.randomUUID().toString();
        StreamSession session = new StreamSession(sessionId, resolvedRequest, emitter, resolvedPath);
        sessions.put(sessionId, session);

        emitter.onCompletion(() -> removeSession(sessionId));
        emitter.onTimeout(() -> removeSession(sessionId));
        emitter.onError(ex -> removeSession(sessionId));

        securityEventLogger.managementOperation(
                new SecurityEventLogger.ManagementAuditEvent("debug_log_stream_open", "backend_logs:" + actor, "success", null));

        List<String> lines = backendLogReadService.readAllLines(session.logPath);
        int start = Math.max(0, lines.size() - session.request.tailLines());
        for (int i = start; i < lines.size(); i++) {
            sendLog(session, lines.get(i));
        }
        session.lastLineIndex = lines.size();

        session.pollingFuture = scheduler.scheduleAtFixedRate(
                () -> pollNewLines(sessionId), POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        session.heartbeatFuture = scheduler.scheduleAtFixedRate(
                () -> sendHeartbeat(sessionId), HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        return emitter;
    }

    private void pollNewLines(String sessionId) {
        StreamSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        if (!backendLogReadService.logExists(session.logPath)) {
            sendErrorEvent(session, ErrorCode.DEBUG_STREAM_SOURCE_UNAVAILABLE.name(), "日志源暂不可用，请稍后重试");
            return;
        }
        List<String> lines = backendLogReadService.readAllLines(session.logPath);
        if (lines.size() < session.lastLineIndex) {
            session.lastLineIndex = 0;
        }
        for (int i = session.lastLineIndex; i < lines.size(); i++) {
            sendLog(session, lines.get(i));
        }
        session.lastLineIndex = lines.size();
    }

    private void sendHeartbeat(String sessionId) {
        StreamSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        try {
            session.emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .data(new HeartbeatEvent(OffsetDateTime.now(ZoneOffset.UTC).toString())));
        } catch (IOException ex) {
            removeSession(sessionId);
        }
    }

    private void sendLog(StreamSession session, String line) {
        DebugLogEvent event = debugLogLineParser.parse(line);
        if (!matchesFilter(event, session.request)) {
            return;
        }
        try {
            session.emitter.send(SseEmitter.event()
                    .id(nextEventId())
                    .name("log")
                    .data(event));
        } catch (IOException ex) {
            removeSession(session.sessionId);
        }
    }

    private boolean matchesFilter(DebugLogEvent event, DebugLogStreamRequest request) {
        if (request.level() != null && !request.level().equals(event.level())) {
            return false;
        }
        if (request.category() != null && !request.category().equals(event.category())) {
            return false;
        }
        return debugLogLineParser.containsKeyword(event, request.keyword());
    }

    private void sendErrorEvent(StreamSession session, String errorCode, String message) {
        try {
            session.emitter.send(SseEmitter.event()
                    .name("error")
                    .data(new ErrorEvent(errorCode, message)));
        } catch (IOException ex) {
            removeSession(session.sessionId);
        }
    }

    private void removeSession(String sessionId) {
        StreamSession removed = sessions.remove(sessionId);
        if (removed == null) {
            return;
        }
        if (removed.pollingFuture != null) {
            removed.pollingFuture.cancel(true);
        }
        if (removed.heartbeatFuture != null) {
            removed.heartbeatFuture.cancel(true);
        }
    }

    private String nextEventId() {
        return Instant.now().toEpochMilli() + "-" + EVENT_SEQ.getAndIncrement();
    }

    /** 日志流请求参数。 */
    public record DebugLogStreamRequest(String level, String category, String keyword, Integer tailLines, String file) {
    }

    /** SSE 日志事件模型。 */
    public record DebugLogEvent(
            String time,
            String level,
            String category,
            String path,
            Integer status,
            Integer costMs,
            String traceId,
            String requestId,
            String service,
            String message) {
    }

    /** 心跳事件模型。 */
    public record HeartbeatEvent(String serverTime) {
    }

    /** 错误事件模型。 */
    public record ErrorEvent(String errorCode, String message) {
    }

    private static final class StreamSession {
        private final String sessionId;
        private final DebugLogStreamRequest request;
        private final SseEmitter emitter;
        private final Path logPath;
        private volatile int lastLineIndex;
        private volatile ScheduledFuture<?> pollingFuture;
        private volatile ScheduledFuture<?> heartbeatFuture;

        private StreamSession(String sessionId, DebugLogStreamRequest request, SseEmitter emitter, Path logPath) {
            this.sessionId = sessionId;
            this.request = request;
            this.emitter = emitter;
            this.logPath = logPath;
        }

    }

    private static final class DebugStreamThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("debug-log-stream");
            thread.setDaemon(true);
            return thread;
        }
    }
}
