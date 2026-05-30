package com.mpfm.backend.common.persistence;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SQL 请求级统计上下文：记录单请求 SQL 次数与最慢 SQL 耗时。
 */
public final class SqlRequestMetricsContext {
    private static final ThreadLocal<Metrics> HOLDER = new ThreadLocal<>();

    private SqlRequestMetricsContext() {
    }

    public static void begin() {
        HOLDER.set(new Metrics());
    }

    public static Metrics end() {
        Metrics metrics = HOLDER.get();
        HOLDER.remove();
        return metrics;
    }

    public static void record(long elapsedMillis) {
        Metrics metrics = HOLDER.get();
        if (metrics == null) {
            return;
        }
        metrics.sqlCount().incrementAndGet();
        metrics.totalElapsedMillis().addAndGet(Math.max(0L, elapsedMillis));
        metrics.maxElapsedMillis().updateAndGet(old -> Math.max(old, elapsedMillis));
    }

    public static boolean active() {
        return HOLDER.get() != null;
    }

    public static final class Metrics {
        private final AtomicInteger sqlCount = new AtomicInteger();
        private final AtomicLong totalElapsedMillis = new AtomicLong();
        private final AtomicLong maxElapsedMillis = new AtomicLong();

        public AtomicInteger sqlCount() {
            return sqlCount;
        }

        public AtomicLong totalElapsedMillis() {
            return totalElapsedMillis;
        }

        public AtomicLong maxElapsedMillis() {
            return maxElapsedMillis;
        }
    }
}

