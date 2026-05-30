package com.mpfm.backend.application.task;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.repository.AsyncTaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 异步任务服务，负责任务创建、状态迁移、查询与取消操作。
 */
@Service
public class AsyncTaskService {
    private final AsyncTaskRepository asyncTaskRepository;
    private final AsyncTaskPersistenceMapper persistenceMapper;
    private final TransferTaskStreamService transferTaskStreamService;

    public AsyncTaskService(AsyncTaskRepository asyncTaskRepository,
                            AsyncTaskPersistenceMapper persistenceMapper,
                            TransferTaskStreamService transferTaskStreamService) {
        this.asyncTaskRepository = asyncTaskRepository;
        this.persistenceMapper = persistenceMapper;
        this.transferTaskStreamService = transferTaskStreamService;
    }

    // 创建
    @Transactional
    public AsyncTask create(String action, String operator, String target) {
        ensureTransferWriteAllowed(action);
        String requestId = MDC.get("requestId") == null ? "" : MDC.get("requestId");
        AsyncTask task = new AsyncTask(
                UUID.randomUUID(), action, operator, target,
                AsyncTaskStatus.PENDING, 0, Instant.now(), Instant.now(), "",
                requestId, "", 0L, 0L);
        return persistenceMapper.fromEntity(asyncTaskRepository.save(persistenceMapper.toEntity(task)));
    }

    @Transactional
    public AsyncTask createBatch(String action, String operator, String target, int totalCount) {
        AsyncTask task = create(action, operator, target);
        return updateProgress(task.id(), 0, Math.max(0, totalCount), 0, 0, 0, List.of());
    }

    @Transactional
    public AsyncTask markRunning(UUID taskId) {
        return updateStatus(taskId, AsyncTaskStatus.PENDING, AsyncTaskStatus.RUNNING);
    }

    @Transactional
    public AsyncTask cancel(UUID taskId, String operator) {
        AsyncTask task = get(taskId);
        ensureTransferWriteAllowed(task.action());
        ensureTransferTerminalWriteAllowed(task.action(), AsyncTaskStatus.CANCELED);
        if (!isCancelable(task.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "task can not be canceled in current state");
        }
        if (!task.operator().equals(operator)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "task operator mismatch");
        }
        return saveTask(task.withStatus(AsyncTaskStatus.CANCELED));
    }

    @Transactional
    public AsyncTask cancelForGovernance(UUID taskId) {
        AsyncTask task = get(taskId);
        ensureTransferTerminalWriteAllowed(task.action(), AsyncTaskStatus.CANCELED);
        if (!isCancelable(task.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "task can not be canceled in current state");
        }
        return saveTask(task.withStatus(AsyncTaskStatus.CANCELED));
    }

    public AsyncTask get(UUID taskId) {
        return asyncTaskRepository.findById(taskId)
                .map(persistenceMapper::fromEntity)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND, "task not found"));
    }

    public List<AsyncTask> listByOperator(String operator) {
        return asyncTaskRepository.findByOperatorOrderByUpdatedAtDesc(operator).stream()
                .map(persistenceMapper::fromEntity)
                .toList();
    }

    // 标记任务成功
    @Transactional
    public AsyncTask markSuccess(UUID taskId) {
        AsyncTask task = get(taskId);
        ensureTransferWriteAllowed(task.action());
        ensureTransferTerminalWriteAllowed(task.action(), AsyncTaskStatus.SUCCESS);
        if (!isRunningLike(task.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "task can not be completed in current state");
        }
        return saveTask(new AsyncTask(task.id(), task.action(), task.operator(), task.target(),
                AsyncTaskStatus.SUCCESS, 100, task.createdAt(), Instant.now(), "",
                task.createdRequestId(), task.payloadJson(), task.transferredBytes(), task.totalBytes()));
    }

    // 标记任务失败
    @Transactional
    public AsyncTask markFailed(UUID taskId, String errorCode) {
        AsyncTask task = get(taskId);
        ensureTransferWriteAllowed(task.action());
        ensureTransferTerminalWriteAllowed(task.action(), AsyncTaskStatus.FAILED);
        if (!isRunningLike(task.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "task can not be failed in current state");
        }
        return saveTask(task.withFailure(errorCode == null ? ErrorCode.INTERNAL_ERROR.name() : errorCode));
    }

    // 更新任务进度
    @Transactional
    public AsyncTask updateProgress(UUID taskId,
                                    int progress,
                                    int totalCount,
                                    int successCount,
                                    int failedCount,
                                    int runningCount,
                                    List<AsyncTask.ItemResult> itemResults) {
        AsyncTask task = get(taskId);
        ensureTransferWriteAllowed(task.action());
        if (!isRunningLike(task.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "task can not update progress in current state");
        }
        return saveTask(task.withProgress(progress));
    }

    // 更新任务分片进度
    @Transactional
    public AsyncTask updateChunkProgress(UUID taskId,
                                         long transferredBytes,
                                         long totalBytes,
                                         long chunkSizeBytes,
                                         int totalChunks,
                                         int completedChunks,
                                         int failedChunks,
                                         List<String> chunkStates,
                                         List<AsyncTask.ItemResult> itemResults) {
        AsyncTask task = get(taskId);
        ensureTransferWriteAllowed(task.action());
        if (!isRunningLike(task.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "task can not update chunk progress in current state");
        }
        return saveTask(task.withChunkProgress(transferredBytes, totalBytes));
    }

    // 清理任务状态列表
    @Transactional
    public long cleanupByStatuses(String operator, List<AsyncTaskStatus> statuses) {
        List<String> statusNames = statuses == null ? List.of() : statuses.stream().map(Enum::name).toList();
        if (statusNames.isEmpty()) {
            return 0;
        }
        return asyncTaskRepository.deleteByOperatorAndStatusIn(operator, statusNames);
    }

    // 删除任务
    @Transactional
    public void deleteTask(UUID taskId, String operator) {
        AsyncTask task = get(taskId);
        if (!task.operator().equals(operator)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "task operator mismatch");
        }
        asyncTaskRepository.deleteById(taskId);
    }

    // 重试任务
    @Transactional
    public AsyncTask retry(UUID taskId, String operator) {
        AsyncTask task = get(taskId);
        ensureTransferWriteAllowed(task.action());
        if (!task.operator().equals(operator)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "task operator mismatch");
        }
        if (task.status() != AsyncTaskStatus.FAILED && task.status() != AsyncTaskStatus.CANCELED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "task can not be retried in current state");
        }
        return saveTask(task.withRetryReset());
    }

    // 更新任务状态
    @Transactional
    public AsyncTask updateStatus(UUID taskId, AsyncTaskStatus nextStatus) {
        AsyncTask task = get(taskId);
        ensureTransferWriteAllowed(task.action());
        ensureTransferTerminalWriteAllowed(task.action(), nextStatus);
        return saveTask(task.withStatus(nextStatus));
    }

    // 根据状态列表查询任务
    public List<AsyncTask> listByStatuses(List<AsyncTaskStatus> statuses) {
        List<String> names = statuses == null ? List.of() : statuses.stream().map(Enum::name).toList();
        if (names.isEmpty()) {
            return List.of();
        }
        return asyncTaskRepository.findByStatusIn(names).stream().map(persistenceMapper::fromEntity).toList();
    }

    // 更新任务负载 JSON
    @Transactional
    public AsyncTask updatePayloadJson(UUID taskId, String payloadJson) {
        AsyncTask task = get(taskId);
        return saveTask(task.withPayloadJson(payloadJson));
    }

    // 是否可取消状态
    private boolean isCancelable(AsyncTaskStatus status) {
        return status == AsyncTaskStatus.PENDING
                || status == AsyncTaskStatus.RUNNING
                || status == AsyncTaskStatus.RETRY_WAITING
                || status == AsyncTaskStatus.RETRYING
                || status == AsyncTaskStatus.PAUSED
                || status == AsyncTaskStatus.RESUMING
                || status == AsyncTaskStatus.PAUSING
                || status == AsyncTaskStatus.CANCELING;
    }

    // 是否运行中状态
    private boolean isRunningLike(AsyncTaskStatus status) {
        return status == AsyncTaskStatus.PENDING
                || status == AsyncTaskStatus.RUNNING
                || status == AsyncTaskStatus.RETRYING
                || status == AsyncTaskStatus.RESUMING
                || status == AsyncTaskStatus.PAUSING;
    }

    // 更新任务状态
    private AsyncTask updateStatus(UUID taskId, AsyncTaskStatus from, AsyncTaskStatus to) {
        AsyncTask current = get(taskId);
        ensureTransferWriteAllowed(current.action());
        ensureTransferTerminalWriteAllowed(current.action(), to);
        if (current.status() != from) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "invalid task transition");
        }
        return saveTask(current.withStatus(to));
    }

    // 保存任务
    private AsyncTask saveTask(AsyncTask task) {
        AsyncTask saved = persistenceMapper.fromEntity(asyncTaskRepository.save(persistenceMapper.toEntity(task)));
        transferTaskStreamService.publish(saved);
        return saved;
    }

    // 确保传输任务写入允许
    private void ensureTransferWriteAllowed(String action) {
        if (!isTransferRuntimeAction(action)) {
            return;
        }
        if (TransferTaskWriteScope.isActive()) {
            return;
        }
        throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "transfer task write must be driven by runtime");
    }

    private boolean isTransferRuntimeAction(String action) {
        return TransferTaskAction.isTransferRuntimeAction(action);
    }

    // 终态写口仅允许在 Runtime Handler 上下文生效，避免调度层与处理器双写。
    private void ensureTransferTerminalWriteAllowed(String action, AsyncTaskStatus targetStatus) {
        if (!isTransferRuntimeAction(action)) {
            return;
        }
        if (!isTerminalStatus(targetStatus)) {
            return;
        }
        if (TransferTaskHandlerWriteScope.isActive()) {
            return;
        }
        throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "transfer terminal state must be written by runtime handler");
    }

    private boolean isTerminalStatus(AsyncTaskStatus status) {
        return status == AsyncTaskStatus.SUCCESS
                || status == AsyncTaskStatus.FAILED
                || status == AsyncTaskStatus.CANCELED;
    }
}

