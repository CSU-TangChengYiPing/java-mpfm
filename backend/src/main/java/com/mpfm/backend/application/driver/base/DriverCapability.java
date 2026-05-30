package com.mpfm.backend.application.driver.base;

/**
 * 驱动能力声明：用于前后端协商协议能力与降级策略。
 */
public record DriverCapability(
        boolean list,
        boolean get,
        boolean link,
        boolean put,
        boolean remove,
        boolean rename,
        boolean move,
        boolean copy,
        boolean makeDir,
        boolean directUpload
) {
    public static DriverCapability full() {
        return new DriverCapability(true, true, true, true, true, true, true, true, true, false);
    }

    public DriverCapability withDirectUpload(boolean enabled) {
        return new DriverCapability(list, get, link, put, remove, rename, move, copy, makeDir, enabled);
    }
}
