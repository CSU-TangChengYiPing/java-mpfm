package com.mpfm.backend.application.driver.base;

import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;

/**
 * 驱动执行上下文：用于隔离挂载元数据与调用方身份。
 */
public record DriverContext(
        String username,
        MountEntity mount
) {
}

