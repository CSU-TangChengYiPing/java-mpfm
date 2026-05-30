package com.mpfm.backend.application.task;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 任务流推送服务：向前端广播任务状态变更事件。
 */
@Service
public class TransferTaskStreamService {
    private final Map<String, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();
    private final Map<String, TaskRateSample> rateSamples = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String username) {
        SseEmitter emitter = new SseEmitter(0L);
        emittersByUser.computeIfAbsent(username, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(username, emitter));
        emitter.onTimeout(() -> remove(username, emitter));
        emitter.onError(ex -> remove(username, emitter));
        return emitter;
    }

    public void publish(AsyncTask task) {
        TaskEvent event = TaskEvent.from(task, rateSamples.compute(task.id().toString(), (id, prev) -> nextSample(task, prev)));
        List<SseEmitter> emitters = emittersByUser.getOrDefault(task.operator(), List.of());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("task").id(task.id().toString()).data(event));
            } catch (IOException ex) {
                remove(task.operator(), emitter);
            }
        }
    }

    private TaskRateSample nextSample(AsyncTask task, TaskRateSample previous) {
        Instant now = task.updatedAt();
        long transferred = Math.max(0L, task.transferredBytes());
        if (previous == null) {
            return new TaskRateSample(now, transferred, 0L, -1L);
        }
        long seconds = Math.max(1L, Duration.between(previous.updatedAt(), now).toSeconds());
        long delta = Math.max(0L, transferred - previous.transferredBytes());
        long speed = delta / seconds;
        long eta = speed <= 0L ? -1L : Math.max(0L, (Math.max(0L, task.totalBytes() - transferred) + speed - 1L) / speed);
        return new TaskRateSample(now, transferred, speed, eta);
    }

    private void remove(String username, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUser.get(username);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(username);
        }
    }

    /** 任务事件模型：对齐前端任务流最小字段集合。 */
    public record TaskEvent(
            String taskId,
            String state,
            int progress,
            long transferredBytes,
            long totalBytes,
            long speedBytesPerSec,
            long etaSeconds,
            String updatedAt,
            String errorCode
    ) {
        static TaskEvent from(AsyncTask task, TaskRateSample sample) {
            String state = switch (task.status()) {
                case PENDING, RETRY_WAITING -> "pending";
                case RUNNING, RETRYING, RESUMING -> "running";
                case PAUSING, PAUSED -> "paused";
                case CANCELING -> "canceling";
                case SUCCESS -> "succeeded";
                case FAILED -> "failed";
                case CANCELED -> "canceled";
            };
            return new TaskEvent(
                    task.id().toString(),
                    state,
                    task.progress(),
                    task.transferredBytes(),
                    task.totalBytes(),
                    sample.speedBytesPerSec(),
                    sample.etaSeconds(),
                    task.updatedAt().toString(),
                    task.errorCode() == null ? "" : task.errorCode()
            );
        }
    }

    private record TaskRateSample(Instant updatedAt, long transferredBytes, long speedBytesPerSec, long etaSeconds) { }
}
