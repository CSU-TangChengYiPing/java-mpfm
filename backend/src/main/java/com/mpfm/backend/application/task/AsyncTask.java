package com.mpfm.backend.application.task;

import java.time.Instant;
import java.util.UUID;

/**
 * 异步任务模型，承载后台任务在生命周期中的状态与上下文字段。
 */
public record AsyncTask(
        UUID id,
        String action,
        String operator,
        String target,
        AsyncTaskStatus status,
        int progress,
        Instant createdAt,
        Instant updatedAt,
        String errorCode,
        String createdRequestId,
        String payloadJson,
        long transferredBytes,
        long totalBytes
) {
    public AsyncTask { }

    /** 兼容历史接口的子项结果结构；任务主模型不再持久化该字段。 */
    public record ItemResult(String itemPath, String status, String errorCode) { }

    // 更新任务状态
    public AsyncTask withStatus(AsyncTaskStatus newStatus) {
        int nextProgress = progress;
        if (newStatus == AsyncTaskStatus.PENDING) {
            nextProgress = 0;
        } else if (newStatus == AsyncTaskStatus.RUNNING || newStatus == AsyncTaskStatus.RETRYING || newStatus == AsyncTaskStatus.RESUMING) {
            nextProgress = Math.max(progress, 1);
        } else if (newStatus == AsyncTaskStatus.PAUSED || newStatus == AsyncTaskStatus.PAUSING || newStatus == AsyncTaskStatus.RETRY_WAITING || newStatus == AsyncTaskStatus.CANCELING) {
            nextProgress = Math.max(0, Math.min(progress, 99));
        } else if (newStatus == AsyncTaskStatus.SUCCESS || newStatus == AsyncTaskStatus.FAILED || newStatus == AsyncTaskStatus.CANCELED) {
            nextProgress = 100;
        }
        return new AsyncTask(id, action, operator, target, newStatus, nextProgress, createdAt, Instant.now(), errorCode,
                createdRequestId, payloadJson, transferredBytes, totalBytes);
    }

    public AsyncTask withProgress(int newProgress) {
        return new AsyncTask(id, action, operator, target, status,
                Math.max(0, Math.min(100, newProgress)), createdAt, Instant.now(), errorCode,
                createdRequestId, payloadJson, transferredBytes, totalBytes);
    }

    public AsyncTask withChunkProgress(long transferred,
                                       long totalBytesValue) {
        int nextProgress = totalBytesValue <= 0 ? 0 : (int) Math.min(100, Math.floor((transferred * 100.0) / totalBytesValue));
        return new AsyncTask(id, action, operator, target, status, nextProgress, createdAt, Instant.now(), errorCode,
                createdRequestId, payloadJson, Math.max(0, transferred), Math.max(0, totalBytesValue));
    }

    public AsyncTask withFailure(String code) {
        return new AsyncTask(id, action, operator, target, AsyncTaskStatus.FAILED, 100, createdAt, Instant.now(),
                code, createdRequestId, payloadJson, transferredBytes, totalBytes);
    }

    public AsyncTask withRetryReset() {
        return new AsyncTask(id, action, operator, target, AsyncTaskStatus.PENDING, 0, createdAt, Instant.now(),
                "", createdRequestId, payloadJson, 0L, totalBytes);
    }

    public AsyncTask withPayloadJson(String nextPayloadJson) {
        return new AsyncTask(id, action, operator, target, status, progress, createdAt, Instant.now(), errorCode,
                createdRequestId, nextPayloadJson == null ? "" : nextPayloadJson, transferredBytes, totalBytes);
    }
}




