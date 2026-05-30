package com.mpfm.backend.application.driver.local;

import com.mpfm.backend.application.driver.base.DriverObject;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

/**
 * Local 驱动对象映射：把 Path 映射为驱动层统一对象。
 */
final class LocalDriverTypes {
    private LocalDriverTypes() {
    }

    static DriverObject toObject(Path path) {
        try {
            boolean directory = Files.isDirectory(path);
            long modifiedMillis = Files.getLastModifiedTime(path).toMillis();
            long size = directory ? 0L : Files.size(path);
            String rawVersion = modifiedMillis + ":" + size;
            String mtime = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(modifiedMillis), ZoneOffset.UTC).toString();
            return new DriverObject(
                    path.toString().replace('\\', '/'),
                    path.getFileName() == null ? "." : path.getFileName().toString(),
                    directory ? "directory" : "file",
                    size,
                    mtime,
                    "\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(rawVersion.getBytes(StandardCharsets.UTF_8)) + "\"");
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "local stat failed", ex);
        }
    }
}
