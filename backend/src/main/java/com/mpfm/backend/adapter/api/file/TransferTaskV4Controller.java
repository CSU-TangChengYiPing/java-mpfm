package com.mpfm.backend.adapter.api.file;

import com.mpfm.backend.application.task.AsyncTask;
import com.mpfm.backend.application.task.AsyncTaskService;
import com.mpfm.backend.application.task.AsyncTaskStatus;
import com.mpfm.backend.application.task.TransferTaskAction;
import com.mpfm.backend.application.task.TransferTaskRuntime;
import com.mpfm.backend.application.task.TransferTaskStreamService;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** v4 任务控制面：统一暴露任务查询、单任务控制与批量控制入口。 */
@RestController
@RequestMapping("/api/v4/transfers/tasks")
public class TransferTaskV4Controller {
    private final AsyncTaskService asyncTaskService;
    private final TransferTaskRuntime transferTaskRuntime;
    private final TransferTaskStreamService transferTaskStreamService;

    public TransferTaskV4Controller(AsyncTaskService asyncTaskService,
                                    TransferTaskRuntime transferTaskRuntime,
                                    TransferTaskStreamService transferTaskStreamService) {
        this.asyncTaskService = asyncTaskService;
        this.transferTaskRuntime = transferTaskRuntime;
        this.transferTaskStreamService = transferTaskStreamService;
    }

    @GetMapping("/undone")
    public List<TaskView> listUndone(Principal principal,
                                     @RequestParam(required = false) String type,
                                     @RequestParam(required = false) String taskGroup) {
        return asyncTaskService.listByOperator(principal.getName()).stream()
                .filter(task -> isUndone(task.status()))
                .filter(task -> type == null || type.isBlank() || task.action().equals(type))
                .filter(task -> taskGroup == null || taskGroup.isBlank() || TransferTaskAction.resolveTaskGroup(task.action()).equalsIgnoreCase(taskGroup))
                .map(TaskView::from)
                .toList();
    }

    @GetMapping("/done")
    public List<TaskView> listDone(Principal principal,
                                   @RequestParam(required = false) String type,
                                   @RequestParam(required = false) String taskGroup) {
        return asyncTaskService.listByOperator(principal.getName()).stream()
                .filter(task -> task.status() == AsyncTaskStatus.SUCCESS
                        || task.status() == AsyncTaskStatus.FAILED
                        || task.status() == AsyncTaskStatus.CANCELED)
                .filter(task -> type == null || type.isBlank() || task.action().equals(type))
                .filter(task -> taskGroup == null || taskGroup.isBlank() || TransferTaskAction.resolveTaskGroup(task.action()).equalsIgnoreCase(taskGroup))
                .map(TaskView::from)
                .toList();
    }

    @GetMapping("/stream")
    public SseEmitter stream(Principal principal) {
        return transferTaskStreamService.subscribe(principal.getName());
    }

    @GetMapping("/{taskId}")
    public TaskView get(@PathVariable UUID taskId, Principal principal) {
        AsyncTask task = asyncTaskService.get(taskId);
        verifyOwner(task, principal.getName());
        return TaskView.from(task);
    }

    @PostMapping("/{taskId}/cancel")
    public TaskView cancel(@PathVariable UUID taskId, Principal principal) {
        return TaskView.from(transferTaskRuntime.cancel(taskId, principal.getName()));
    }

    @PostMapping("/{taskId}/retry")
    public TaskView retry(@PathVariable UUID taskId, Principal principal) {
        return TaskView.from(transferTaskRuntime.retry(taskId, principal.getName()));
    }

    @PostMapping("/{taskId}/pause")
    public TaskView pause(@PathVariable UUID taskId, Principal principal) {
        return TaskView.from(transferTaskRuntime.pause(taskId, principal.getName()));
    }

    @PostMapping("/{taskId}/resume")
    public TaskView resume(@PathVariable UUID taskId, Principal principal) {
        return TaskView.from(transferTaskRuntime.resume(taskId, principal.getName()));
    }

    @DeleteMapping("/{taskId}")
    public DeleteResponse delete(@PathVariable UUID taskId, Principal principal) {
        asyncTaskService.deleteTask(taskId, principal.getName());
        return new DeleteResponse(taskId.toString(), "success");
    }

    @PostMapping("/cancel")
    public BatchResponse batchCancel(@RequestBody BatchTaskRequest request, Principal principal) {
        return executeBatch(request, principal.getName(), taskId -> transferTaskRuntime.cancel(taskId, principal.getName()));
    }

    @PostMapping("/retry")
    public BatchResponse batchRetry(@RequestBody BatchTaskRequest request, Principal principal) {
        return executeBatch(request, principal.getName(), taskId -> transferTaskRuntime.retry(taskId, principal.getName()));
    }

    @PostMapping("/delete")
    public BatchResponse batchDelete(@RequestBody BatchTaskRequest request, Principal principal) {
        return executeBatch(request, principal.getName(), taskId -> {
            asyncTaskService.deleteTask(taskId, principal.getName());
        });
    }

    @PostMapping("/clear-done")
    public CleanupResponse clearDone(@RequestBody ClearDoneRequest request, Principal principal) {
        List<AsyncTaskStatus> statuses;
        if (request.status() == null || request.status().isBlank()) {
            statuses = List.of(AsyncTaskStatus.SUCCESS, AsyncTaskStatus.FAILED, AsyncTaskStatus.CANCELED);
        } else {
            statuses = List.of(AsyncTaskStatus.valueOf(request.status().toUpperCase(Locale.ROOT)));
        }
        long deleted = asyncTaskService.cleanupByStatuses(principal.getName(), statuses);
        return new CleanupResponse(deleted);
    }

    private BatchResponse executeBatch(BatchTaskRequest request, String operator, TaskAction action) {
        List<String> errors = request.taskIds() == null ? List.of() : request.taskIds().stream()
                .map(raw -> runSingleAction(raw, operator, action))
                .filter(value -> !value.isEmpty())
                .toList();
        return new BatchResponse(request.taskIds() == null ? 0 : request.taskIds().size(), errors.size(), errors);
    }

    private String runSingleAction(String rawTaskId, String operator, TaskAction action) {
        try {
            UUID taskId = UUID.fromString(rawTaskId);
            AsyncTask task = asyncTaskService.get(taskId);
            verifyOwner(task, operator);
            action.run(taskId);
            return "";
        } catch (Exception ex) {
            return rawTaskId + ":" + ex.getMessage();
        }
    }

    private void verifyOwner(AsyncTask task, String operator) {
        if (!task.operator().equals(operator)) {
            throw new com.mpfm.backend.common.error.BusinessException(
                    com.mpfm.backend.common.error.ErrorCode.TASK_NOT_FOUND, "task not found");
        }
    }

    @FunctionalInterface
    private interface TaskAction {
        void run(UUID taskId);
    }

    /** v4 任务公开视图：对齐 OpenList 任务页语义，屏蔽后端内部字段。 */
    public record TaskView(String id, String name, String creator, String taskGroup, String state, String status,
                           double progress, String startTime, String endTime, long totalBytes, String error) {
        static TaskView from(AsyncTask task) {
            String state = switch (task.status()) {
                case PENDING, RETRY_WAITING -> "pending";
                case RUNNING, RETRYING, RESUMING -> "running";
                case PAUSING, PAUSED -> "paused";
                case CANCELING -> "canceling";
                case SUCCESS -> "succeeded";
                case FAILED -> "failed";
                case CANCELED -> "canceled";
            };
            String status = task.status().name().toLowerCase(Locale.ROOT);
            String endTime = (task.status() == AsyncTaskStatus.SUCCESS
                    || task.status() == AsyncTaskStatus.FAILED
                    || task.status() == AsyncTaskStatus.CANCELED) ? task.updatedAt().toString() : null;
            String error = task.errorCode() == null || task.errorCode().isBlank() ? null : task.errorCode();
            return new TaskView(
                    task.id().toString(),
                    task.action(),
                    task.operator(),
                    TransferTaskAction.resolveTaskGroup(task.action()),
                    state,
                    status,
                    task.progress(),
                    task.createdAt().toString(),
                    endTime,
                    Math.max(task.totalBytes(), task.transferredBytes()),
                    error
            );
        }
    }

    /** 批量任务请求：承载任务 ID 列表。 */
    public record BatchTaskRequest(List<String> taskIds) { }

    /** 批量操作响应：返回总量、失败量与失败明细。 */
    public record BatchResponse(int totalCount, int failedCount, List<String> errors) { }

    /** 清理完成任务请求：可按状态过滤清理范围。 */
    public record ClearDoneRequest(String status) { }

    /** 清理结果：返回删除任务数量。 */
    public record CleanupResponse(long deletedCount) { }

    /** 删除结果：返回任务标识与执行状态。 */
    public record DeleteResponse(String taskId, String status) { }

    private boolean isUndone(AsyncTaskStatus status) {
        return status == AsyncTaskStatus.PENDING
                || status == AsyncTaskStatus.RUNNING
                || status == AsyncTaskStatus.PAUSING
                || status == AsyncTaskStatus.PAUSED
                || status == AsyncTaskStatus.RESUMING
                || status == AsyncTaskStatus.RETRY_WAITING
                || status == AsyncTaskStatus.RETRYING
                || status == AsyncTaskStatus.CANCELING;
    }
}
