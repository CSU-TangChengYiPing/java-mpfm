package com.mpfm.backend.application.file;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import com.mpfm.backend.infrastructure.persistence.entity.MountEntity;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
class FileEntryMapper {
    private static final String PERSONAL_PREFIX = "./personal/";
    private static final String SHARED_PREFIX = "./shared/";
    private static final String LOCAL_TYPE = "local";

    FileApplicationService.EntryResult toEntry(MountEntity mount, Path path, boolean shared,
                                               boolean visible, boolean readable, boolean writable) {
        try {
            String rel = toRelativePath(mount, path);
            FileTime time = Files.getLastModifiedTime(path);
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            Path fileName = path.getFileName();
            String name = fileName == null ? path.toString() : fileName.toString();
            String base = shared ? SHARED_PREFIX + mount.getName() : PERSONAL_PREFIX + mount.getName();
            long size = Files.isDirectory(path) ? 0L : Files.size(path);
            String version = attrs.lastModifiedTime().toMillis() + ":" + size;
            return new FileApplicationService.EntryResult(
                    base + (rel.isBlank() ? "" : "/" + rel),
                    name,
                    Files.isDirectory(path) ? "directory" : "file",
                    size,
                    time.toInstant().toString(),
                    null,
                    visible,
                    readable,
                    writable,
                    "\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(version.getBytes(StandardCharsets.UTF_8)) + "\"",
                    version
            );
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "stat failed", ex);
        }
    }

    FileApplicationService.EntryResult toMinimalEntry(MountEntity mount, Path path, boolean shared) {
        String rel = toRelativePath(mount, path);
        Path fileName = path.getFileName();
        String name = fileName == null ? path.toString() : fileName.toString();
        String base = shared ? SHARED_PREFIX + mount.getName() : PERSONAL_PREFIX + mount.getName();
        return new FileApplicationService.EntryResult(
                base + (rel.isBlank() || ".".equals(rel) ? "" : "/" + rel),
                name,
                Files.isDirectory(path) ? "directory" : "file",
                0L,
                "",
                null,
                true,
                false,
                false,
                "",
                ""
        );
    }

    FileApplicationService.EntryResult toEntry(MountEntity mount, Path path, boolean shared) {
        return toEntry(mount, path, shared, true, true, true);
    }

    private String toRelativePath(MountEntity mount, Path path) {
        if (LOCAL_TYPE.equalsIgnoreCase(mount.getType())) {
            return Path.of(mount.getPhysicalRoot()).relativize(path).toString().replace('\\', '/');
        }
        String normalized = path.toString().replace('\\', '/');
        if (normalized.isBlank() || ".".equals(normalized) || "/".equals(normalized)) {
            return ".";
        }
        String base = remoteBasePath(mount);
        if (".".equals(base)) {
            return normalized.startsWith("/") ? normalized.substring(1) : normalized;
        }
        String baseWithSlash = base.endsWith("/") ? base : base + "/";
        if (normalized.equals(base)) {
            return ".";
        }
        if (normalized.startsWith(baseWithSlash)) {
            return normalized.substring(baseWithSlash.length());
        }
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    private String remoteBasePath(MountEntity mount) {
        try {
            URI uri = URI.create(mount.getPhysicalRoot());
            String rawPath = uri.getPath();
            if (rawPath == null || rawPath.isBlank() || "/".equals(rawPath)) {
                return ".";
            }
            String normalized = rawPath.replace('\\', '/');
            return normalized.startsWith("/") ? normalized.substring(1) : normalized;
        } catch (Exception ex) {
            return ".";
        }
    }
}


