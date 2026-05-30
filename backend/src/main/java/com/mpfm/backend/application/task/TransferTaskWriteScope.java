package com.mpfm.backend.application.task;

/**
 * 传输任务写入作用域：用于约束公共任务态只能由 Runtime 执行链写入。
 */
public final class TransferTaskWriteScope {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private TransferTaskWriteScope() { }

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        int next = DEPTH.get() - 1;
        if (next <= 0) {
            DEPTH.remove();
            return;
        }
        DEPTH.set(next);
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }
}

