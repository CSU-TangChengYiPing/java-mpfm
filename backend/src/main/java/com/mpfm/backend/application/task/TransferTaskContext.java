package com.mpfm.backend.application.task;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** 任务执行上下文：向执行器暴露任务标识与暂停/取消协作信号。 */
public final class TransferTaskContext {
    private final UUID taskUuid;
    private final AtomicBoolean paused;
    private final AtomicBoolean canceled;

    TransferTaskContext(UUID taskId, AtomicBoolean paused, AtomicBoolean canceled) {
        this.taskUuid = taskId;
        this.paused = paused;
        this.canceled = canceled;
    }

    public UUID taskId() {
        return taskUuid;
    }

    public boolean isPaused() {
        return paused.get();
    }

    public boolean isCanceled() {
        return canceled.get();
    }

    public void ensureNotCanceled() {
        if (isCanceled()) {
            throw new TransferTaskControlException("task canceled");
        }
    }
}
