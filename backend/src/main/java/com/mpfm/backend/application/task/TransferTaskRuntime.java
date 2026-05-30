package com.mpfm.backend.application.task;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/** 任务运行时：统一调度执行、暂停恢复、重试退避与重启恢复。 */
@Service
public class TransferTaskRuntime {
    private final AsyncTaskService asyncTaskService;
    private final TransferTaskRegistry registry;
    private final TransferRetryPolicy retryPolicy;
    private final TransferTaskAggregateMapper aggregateMapper;
    private final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicBoolean> pausedFlags = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicBoolean> canceledFlags = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> retryCounters = new ConcurrentHashMap<>();

    public TransferTaskRuntime(AsyncTaskService asyncTaskService,
                               TransferTaskRegistry registry,
                               TransferRetryPolicy retryPolicy,
                               TransferTaskAggregateMapper aggregateMapper) {
        this.asyncTaskService = asyncTaskService;
        this.registry = registry;
        this.retryPolicy = retryPolicy;
        this.aggregateMapper = aggregateMapper;
    }

    // 初始化时恢复待处理任务
    // 1. 恢复所有待处理任务
    // 2. 恢复所有暂停任务
    // 3. 恢复所有取消任务
    // 4. 恢复所有重试任务
    @PostConstruct
    void recoverPendingTasks() {
        List<AsyncTask> recoverable = asyncTaskService.listByStatuses(List.of(
                AsyncTaskStatus.PENDING,
                AsyncTaskStatus.RUNNING,
                AsyncTaskStatus.RETRY_WAITING,
                AsyncTaskStatus.RETRYING,
                AsyncTaskStatus.RESUMING,
                AsyncTaskStatus.PAUSING,
                AsyncTaskStatus.CANCELING
        ));
        for (AsyncTask task : recoverable) {
            if (task.status() == AsyncTaskStatus.PAUSING) {
                runInWriteScope(() -> asyncTaskService.updateStatus(task.id(), AsyncTaskStatus.PAUSED));
                continue;
            }
            if (task.status() == AsyncTaskStatus.CANCELING) {
                runInTerminalWriteScope(() -> asyncTaskService.updateStatus(task.id(), AsyncTaskStatus.CANCELED));
                continue;
            }
            enqueue(task.id());
        }
    }

    // 提交任务
    public AsyncTask submit(String type, String operator, String target) {
        registry.require(type);
        AsyncTask task = runInWriteScope(() -> asyncTaskService.create(type, operator, target));
        enqueue(task.id());
        return task;
    }

    // 暂停任务
    public AsyncTask pause(UUID taskId, String operator) {
        AsyncTask task = asyncTaskService.get(taskId);
        verifyOwner(task, operator);
        if (task.status() != AsyncTaskStatus.RUNNING && task.status() != AsyncTaskStatus.RETRYING && task.status() != AsyncTaskStatus.RESUMING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "task can not be paused in current state");
        }
        pausedFlags.computeIfAbsent(taskId, ignored -> new AtomicBoolean(false)).set(true);
        AsyncTask pausing = runInWriteScope(() -> asyncTaskService.updateStatus(taskId, AsyncTaskStatus.PAUSING));
        return runInWriteScope(() -> asyncTaskService.updateStatus(pausing.id(), AsyncTaskStatus.PAUSED));
    }

    // 恢复任务
    public AsyncTask resume(UUID taskId, String operator) {
        AsyncTask task = asyncTaskService.get(taskId);
        verifyOwner(task, operator);
        if (task.status() != AsyncTaskStatus.PAUSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "task can not be resumed in current state");
        }
        pausedFlags.computeIfAbsent(taskId, ignored -> new AtomicBoolean(false)).set(false);
        runInWriteScope(() -> asyncTaskService.updateStatus(taskId, AsyncTaskStatus.RESUMING));
        enqueue(taskId);
        return asyncTaskService.get(taskId);
    }

    // 取消任务
    public AsyncTask cancel(UUID taskId, String operator) {
        AsyncTask task = asyncTaskService.get(taskId);
        verifyOwner(task, operator);
        canceledFlags.computeIfAbsent(taskId, ignored -> new AtomicBoolean(false)).set(true);
        if (task.status() == AsyncTaskStatus.RUNNING || task.status() == AsyncTaskStatus.RETRYING || task.status() == AsyncTaskStatus.RESUMING) {
            runInWriteScope(() -> asyncTaskService.updateStatus(taskId, AsyncTaskStatus.CANCELING));
        }
        return runInTerminalWriteScope(() -> asyncTaskService.cancel(taskId, operator));
    }

    // 重试任务
    public AsyncTask retry(UUID taskId, String operator) {
        AsyncTask task = asyncTaskService.get(taskId);
        verifyOwner(task, operator);
        retryCounters.remove(taskId);
        AsyncTask reset = runInWriteScope(() -> asyncTaskService.retry(taskId, operator));
        enqueue(reset.id());
        return reset;
    }

    // 队列任务
    private void enqueue(UUID taskId) {
        AsyncTask task = asyncTaskService.get(taskId);
        TransferTaskTypeConfig config = registry.require(task.action());
        executors.computeIfAbsent(config.type(), ignored -> Executors.newFixedThreadPool(config.workers()))
                .submit(() -> runTask(taskId, config));
    }

    // 运行任务
    private void runTask(UUID taskId, TransferTaskTypeConfig config) {
        AsyncTask current = asyncTaskService.get(taskId);
        TransferTaskAggregate aggregate = aggregateMapper.from(current);
        if (isTerminalOrBlocked(aggregate.status())) {
            return;
        }
        AtomicBoolean paused = pausedFlags.computeIfAbsent(taskId, ignored -> new AtomicBoolean(false));
        AtomicBoolean canceled = canceledFlags.computeIfAbsent(taskId, ignored -> new AtomicBoolean(false));
        if (handleControlSignals(taskId, paused, canceled)) {
            return;
        }
        switchToRunning(taskId, aggregate.status());
        TransferTaskContext context = new TransferTaskContext(taskId, paused, canceled);
        try {
            runInWriteScopeChecked(() -> config.handler().execute(context));
            if (handleContextCompletion(taskId, context)) {
                return;
            }
            AsyncTask latest = asyncTaskService.get(taskId);
            if (latest.status() == AsyncTaskStatus.SUCCESS
                    || latest.status() == AsyncTaskStatus.FAILED
                    || latest.status() == AsyncTaskStatus.CANCELED) {
                return;
            }
            if (!isTransferRuntimeAction(latest.action())) {
                runInWriteScope(() -> asyncTaskService.markSuccess(taskId));
            }
        } catch (TransferTaskControlException ex) {
            runInTerminalWriteScope(() -> asyncTaskService.updateStatus(taskId, AsyncTaskStatus.CANCELED));
        } catch (Exception ex) {
            scheduleRetry(taskId, config);
        }
    }

    // 重试任务
    private void scheduleRetry(UUID taskId, TransferTaskTypeConfig config) {
        int nextAttempt = retryCounters.computeIfAbsent(taskId, ignored -> new AtomicInteger(0)).incrementAndGet();
        if (nextAttempt > config.maxRetry()) {
            runInTerminalWriteScope(() -> asyncTaskService.markFailed(taskId, "INTERNAL_ERROR"));
            return;
        }
        runInWriteScope(() -> asyncTaskService.updateStatus(taskId, AsyncTaskStatus.RETRY_WAITING));
        long delayMs = retryPolicy.backoff(nextAttempt).toMillis();
        retryScheduler.schedule(() -> enqueue(taskId), delayMs, TimeUnit.MILLISECONDS);
    }

    // 验证任务所有者
    private void verifyOwner(AsyncTask task, String operator) {
        if (!task.operator().equals(operator)) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "task not found");
        }
    }

    // 检查任务是否为终端状态或阻塞状态
    private boolean isTerminalOrBlocked(AsyncTaskStatus status) {
        return status == AsyncTaskStatus.PAUSED
                || status == AsyncTaskStatus.CANCELED
                || status == AsyncTaskStatus.SUCCESS;
    }

    // 处理任务控制信号
    private boolean handleControlSignals(UUID taskId, AtomicBoolean paused, AtomicBoolean canceled) {
        if (canceled.get()) {
            runInTerminalWriteScope(() -> asyncTaskService.updateStatus(taskId, AsyncTaskStatus.CANCELED));
            return true;
        }
        if (paused.get()) {
            runInWriteScope(() -> asyncTaskService.updateStatus(taskId, AsyncTaskStatus.PAUSED));
            return true;
        }
        return false;
    }

    // 切换任务运行状态
    private void switchToRunning(UUID taskId, AsyncTaskStatus status) {
        if (status == AsyncTaskStatus.RETRY_WAITING) {
            runInWriteScope(() -> asyncTaskService.updateStatus(taskId, AsyncTaskStatus.RETRYING));
        } else if (status == AsyncTaskStatus.RESUMING) {
            runInWriteScope(() -> asyncTaskService.updateStatus(taskId, AsyncTaskStatus.RUNNING));
        } else {
            runInWriteScope(() -> asyncTaskService.markRunning(taskId));
        }
    }

    // 处理任务上下文完成信号
    private boolean handleContextCompletion(UUID taskId, TransferTaskContext context) {
        if (context.isCanceled()) {
            runInTerminalWriteScope(() -> asyncTaskService.updateStatus(taskId, AsyncTaskStatus.CANCELED));
            return true;
        }
        if (context.isPaused()) {
            runInWriteScope(() -> asyncTaskService.updateStatus(taskId, AsyncTaskStatus.PAUSED));
            return true;
        }
        return false;
    }

    private boolean isTransferRuntimeAction(String action) {
        return TransferTaskAction.isTransferRuntimeAction(action);
    }

    // 运行任务上下文
    private <T> T runInWriteScope(Supplier<T> supplier) {
        TransferTaskWriteScope.enter();
        try {
            return supplier.get();
        } finally {
            TransferTaskWriteScope.exit();
        }
    }
 
    private void runInWriteScopeChecked(CheckedRunnable runnable) throws Exception {
        TransferTaskWriteScope.enter();
        TransferTaskHandlerWriteScope.enter();
        try {
            runnable.run();
        } finally {
            TransferTaskHandlerWriteScope.exit();
            TransferTaskWriteScope.exit();
        }
    }

    private <T> T runInTerminalWriteScope(Supplier<T> supplier) {
        TransferTaskWriteScope.enter();
        TransferTaskHandlerWriteScope.enter();
        try {
            return supplier.get();
        } finally {
            TransferTaskHandlerWriteScope.exit();
            TransferTaskWriteScope.exit();
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    @PreDestroy
    void shutdownExecutors() {
        retryScheduler.shutdownNow();
        executors.values().forEach(ExecutorService::shutdownNow);
    }
}
