package com.mpfm.backend.application.driver.base;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 驱动工厂：按 mount.type 选择协议驱动实现。
 */
@Component
public class DriverFactory {
    private final Map<String, StorageDriver> drivers;

    public DriverFactory(List<StorageDriver> drivers) {
        this.drivers = drivers.stream().collect(Collectors.toMap(
                it -> it.protocol().toLowerCase(Locale.ROOT),
                Function.identity(),
                (a, b) -> a));
    }

    public StorageDriver resolve(String protocol) {
        String key = protocol == null ? "" : protocol.trim().toLowerCase(Locale.ROOT);
        StorageDriver driver = drivers.get(key);
        if (driver == null) {
            throw new BusinessException(ErrorCode.CAPABILITY_NOT_SUPPORTED, "unsupported driver protocol: " + protocol);
        }
        return driver;
    }
}

