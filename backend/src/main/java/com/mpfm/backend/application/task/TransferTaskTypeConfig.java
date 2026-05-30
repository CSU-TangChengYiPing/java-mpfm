package com.mpfm.backend.application.task;

/** 任务类型配置：定义 worker 并发、最大重试和执行器绑定。 */
public record TransferTaskTypeConfig(String type, int workers, int maxRetry, TransferTaskHandler handler) {
    public TransferTaskTypeConfig {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type required");
        }
        if (workers <= 0) {
            throw new IllegalArgumentException("workers must be positive");
        }
        if (maxRetry < 0) {
            throw new IllegalArgumentException("maxRetry must be non-negative");
        }
    }
}
