package com.mpfm.backend.application.task;

import java.time.Instant;
import java.util.UUID;

/** 任务运行聚合：承载运行时决策所需的最小领域信息，不直接暴露给 API。 */
public record TransferTaskAggregate(
        UUID taskId,
        String type,
        String owner,
        String target,
        AsyncTaskStatus status,
        int progress,
        Instant createdAt,
        Instant updatedAt,
        String errorCode,
        long transferredBytes,
        long totalBytes
) {
}
