package com.mpfm.backend.application.driver.base;

/**
 * 驱动上传请求模型：统一封装目标目录、文件名、覆盖策略与内容。
 */
public record DriverPutRequest(
        String dstDirPath,
        String fileName,
        byte[] content,
        boolean overwrite
) {
}

