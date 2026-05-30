package com.mpfm.backend.application.driver.local;

import com.mpfm.backend.application.driver.base.DriverContext;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Local 驱动工具类：统一路径校验与基础文件操作异常封装。
 */
final class LocalDriverUtil {
    private LocalDriverUtil() {
    }

    static Path resolveUnderMount(DriverContext context, String rawPath) {
        String normalized = (rawPath == null || rawPath.isBlank() || ".".equals(rawPath)) ? "" : rawPath.replace('\\', '/');
        if (normalized.contains("..")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid path");
        }
        Path root = Path.of(context.mount().getPhysicalRoot()).normalize();
        Path resolved = normalized.isBlank() ? root : root.resolve(normalized.startsWith("/") ? normalized.substring(1) : normalized).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid path");
        }
        return resolved;
    }

    static void move(Path src, Path dst) {
        try {
            Files.createDirectories(dst.getParent());
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "local move failed", ex);
        }
    }

    static void copy(Path src, Path dst) {
        try {
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "local copy failed", ex);
        }
    }

    static void delete(Path target) {
        try {
            if (Files.isDirectory(target)) {
                Files.walk(target).sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "local delete failed", ex);
                    }
                });
                return;
            }
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "local delete failed", ex);
        }
    }

    static void write(Path target, byte[] content, boolean overwrite) {
        try {
            Files.createDirectories(target.getParent());
            if (!overwrite && Files.exists(target)) {
                throw new BusinessException(ErrorCode.CONFLICT, "file exists");
            }
            Files.write(target, content);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "local put failed", ex);
        }
    }
}

