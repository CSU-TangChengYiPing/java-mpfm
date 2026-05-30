package com.mpfm.backend.application.task;

import java.time.Duration;
import org.springframework.stereotype.Component;

/** 重试策略：提供指数退避延迟计算，避免失败风暴放大。 */
@Component
public class TransferRetryPolicy {
    private static final long BASE_DELAY_MS = 500L;
    private static final long MAX_DELAY_MS = 10_000L;

    public Duration backoff(int attempt) {
        int safe = Math.max(1, attempt);
        long delay = BASE_DELAY_MS * (1L << Math.min(6, safe - 1));
        return Duration.ofMillis(Math.min(MAX_DELAY_MS, delay));
    }
}
