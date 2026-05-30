package com.mpfm.backend.application.driver.base;

import java.util.Map;

/**
 * 驱动层下载链接模型：统一描述直链地址、头与过期时间。
 */
public record DriverLink(
        String url,
        Map<String, String> headers,
        String expiresAt
) {
}

