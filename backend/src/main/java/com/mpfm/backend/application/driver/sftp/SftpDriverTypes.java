package com.mpfm.backend.application.driver.sftp;

import com.mpfm.backend.application.driver.base.DriverObject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.sftp.client.SftpClient;

/**
 * SFTP 类型映射：用于把 SFTP 条目归一到统一对象模型。
 */
public final class SftpDriverTypes {
    private SftpDriverTypes() {
    }

    public static DriverObject toObject(String mountBasePath, SftpClient.DirEntry entry) {
        SftpClient.Attributes attrs = entry.getAttributes();
        String normalizedBase = SftpDriverUtil.normalizePath(mountBasePath);
        String fullPath = SftpDriverUtil.join(normalizedBase, entry.getFilename());
        String type = attrs.isDirectory() ? "directory" : "file";
        long size = attrs.isDirectory() ? 0L : attrs.getSize();
        long mtimeSeconds = attrs.getModifyTime() == null ? 0L : attrs.getModifyTime().to(TimeUnit.SECONDS);
        String mtime = OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(mtimeSeconds), ZoneOffset.UTC).toString();
        String etag = Long.toHexString(size) + "-" + Long.toHexString(mtimeSeconds);
        String normalizedPath = fullPath.replace('\\', '/').replaceAll("/+", "/");
        return new DriverObject(normalizedPath,
                entry.getFilename(), type, size, mtime, etag);
    }

}
