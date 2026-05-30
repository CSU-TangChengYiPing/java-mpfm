package com.mpfm.backend.application.driver.base;

/**
 * 驱动层统一对象模型：用于跨协议返回文件与目录元信息。
 */
public record DriverObject(
        String path,
        String name,
        String type,
        long sizeBytes,
        String mtime,
        String etag
) {
}

