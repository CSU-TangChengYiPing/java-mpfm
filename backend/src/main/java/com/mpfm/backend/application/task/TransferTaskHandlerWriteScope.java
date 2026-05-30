package com.mpfm.backend.application.task;

/**
 * 任务处理器写口作用域：仅允许在 Runtime Handler 执行期写入任务终态。
 * 该作用域用于阻断“调度层/控制器层直接写 SUCCESS/FAILED/CANCELED”的双写风险。
 */
public final class TransferTaskHandlerWriteScope {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private TransferTaskHandlerWriteScope() {
    }

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

