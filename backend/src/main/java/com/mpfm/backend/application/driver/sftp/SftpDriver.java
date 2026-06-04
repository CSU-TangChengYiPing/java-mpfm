package com.mpfm.backend.application.driver.sftp;

import com.mpfm.backend.application.driver.base.DriverCapability;
import com.mpfm.backend.application.driver.base.DriverContext;
import com.mpfm.backend.application.driver.base.DriverLink;
import com.mpfm.backend.application.driver.base.DriverObject;
import com.mpfm.backend.application.driver.base.DriverPutRequest;
import com.mpfm.backend.application.driver.base.StorageDriver;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.stereotype.Component;

/**
 * SFTP 协议驱动骨架。
 */
@Component
public class SftpDriver implements StorageDriver {
    @Override
    public String protocol() {
        return "sftp";
    }

    @Override
    public DriverCapability capability() {
        return new DriverCapability(true, true, true, true, true, true, true, false, true, false);
    }

    @Override
    public void init(DriverContext context) {
        SftpDriverUtil.SftpConnection connection = SftpDriverUtil.open(context);
        try {
            connection.sftpClient().stat(connection.basePath().equals(".") ? "/" : connection.basePath());
        } catch (IOException ex) {
            SftpDriverUtil.invalidate(context);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "sftp init failed", ex);
        } finally {
            SftpDriverUtil.closeQuietly(connection);
        }
    }

    @Override
    public List<DriverObject> list(DriverContext context, String dirPath) {
        SftpDriverUtil.SftpConnection connection = SftpDriverUtil.open(context);
        String target = buildTargetPath(connection.basePath(), dirPath);
        try {
            List<DriverObject> objects = new ArrayList<>();
            for (SftpClient.DirEntry entry : connection.sftpClient().readDir(target)) {
                if (".".equals(entry.getFilename()) || "..".equals(entry.getFilename())) {
                    continue;
                }
                objects.add(SftpDriverTypes.toObject(target, entry));
            }
            return objects;
        } catch (IOException ex) {
            SftpDriverUtil.invalidate(context);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "sftp list failed", ex);
        } finally {
            SftpDriverUtil.closeQuietly(connection);
        }
    }

    @Override
    public DriverObject get(DriverContext context, String path) {
        SftpDriverUtil.SftpConnection connection = SftpDriverUtil.open(context);
        String target = buildTargetPath(connection.basePath(), path);
        try {
            SftpClient.Attributes attrs = connection.sftpClient().stat(target);
            String name = target.equals("/") ? "/" : target.substring(target.lastIndexOf('/') + 1);
            String type = attrs.isDirectory() ? "directory" : "file";
            long size = attrs.isDirectory() ? 0L : attrs.getSize();
            long mtimeSeconds = attrs.getModifyTime() == null ? 0L : attrs.getModifyTime().to(java.util.concurrent.TimeUnit.SECONDS);
            String mtime = OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(mtimeSeconds), ZoneOffset.UTC).toString();
            String etag = Long.toHexString(size) + "-" + Long.toHexString(mtimeSeconds);
            return new DriverObject(target, name, type, size, mtime, etag);
        } catch (IOException ex) {
            SftpDriverUtil.invalidate(context);
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "sftp path not found", ex);
        } finally {
            SftpDriverUtil.closeQuietly(connection);
        }
    }

    @Override
    public DriverLink link(DriverContext context, String filePath) {
        return new DriverLink(filePath, Map.of(), null);
    }

    @Override
    public void makeDir(DriverContext context, String parentDirPath, String dirName) {
        SftpDriverUtil.SftpConnection connection = SftpDriverUtil.open(context);
        String target = SftpDriverUtil.join(buildTargetPath(connection.basePath(), parentDirPath), dirName);
        try {
            connection.sftpClient().mkdir(target);
        } catch (IOException ex) {
            SftpDriverUtil.invalidate(context);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "sftp mkdir failed", ex);
        } finally {
            SftpDriverUtil.closeQuietly(connection);
        }
    }

    @Override
    public void move(DriverContext context, String srcPath, String dstDirPath) {
        SftpDriverUtil.SftpConnection connection = SftpDriverUtil.open(context);
        String src = buildTargetPath(connection.basePath(), srcPath);
        String dstDir = buildTargetPath(connection.basePath(), dstDirPath);
        String dst = SftpDriverUtil.join(dstDir, src.substring(src.lastIndexOf('/') + 1));
        try {
            connection.sftpClient().rename(src, dst);
        } catch (IOException ex) {
            SftpDriverUtil.invalidate(context);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "sftp move failed", ex);
        } finally {
            SftpDriverUtil.closeQuietly(connection);
        }
    }

    @Override
    public void rename(DriverContext context, String srcPath, String newName) {
        SftpDriverUtil.SftpConnection connection = SftpDriverUtil.open(context);
        String src = buildTargetPath(connection.basePath(), srcPath);
        String parent = src.contains("/") ? src.substring(0, src.lastIndexOf('/')) : "/";
        String dst = SftpDriverUtil.join(parent, newName);
        try {
            connection.sftpClient().rename(src, dst);
        } catch (IOException ex) {
            SftpDriverUtil.invalidate(context);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "sftp rename failed", ex);
        } finally {
            SftpDriverUtil.closeQuietly(connection);
        }
    }

    @Override
    public void copy(DriverContext context, String srcPath, String dstDirPath) {
        // copy暂未实现
        throw new BusinessException(ErrorCode.CAPABILITY_NOT_SUPPORTED, "sftp copy not supported");
    }

    @Override
    public void remove(DriverContext context, String path) {
        SftpDriverUtil.SftpConnection connection = SftpDriverUtil.open(context);
        String target = buildTargetPath(connection.basePath(), path);
        try {
            SftpClient.Attributes attrs = connection.sftpClient().stat(target);
            if (attrs.isDirectory()) {
                deleteDirRecursive(connection.sftpClient(), target);
            } else {
                connection.sftpClient().remove(target);
            }
        } catch (IOException ex) {
            SftpDriverUtil.invalidate(context);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "sftp remove failed", ex);
        } finally {
            SftpDriverUtil.closeQuietly(connection);
        }
    }

    @Override
    public DriverObject put(DriverContext context, DriverPutRequest request) {
        SftpDriverUtil.SftpConnection connection = SftpDriverUtil.open(context);
        String dir = buildTargetPath(connection.basePath(), request.dstDirPath());
        String target = SftpDriverUtil.join(dir, request.fileName());
        try (SftpClient.CloseableHandle handle = connection.sftpClient().open(target,
                SftpClient.OpenMode.Create, SftpClient.OpenMode.Write, SftpClient.OpenMode.Truncate)) {
            byte[] data = request.content() == null ? new byte[0] : request.content();
            connection.sftpClient().write(handle, 0L, data, 0, data.length);
            return get(context, SftpDriverUtil.join(request.dstDirPath(), request.fileName()));
        } catch (IOException ex) {
            SftpDriverUtil.invalidate(context);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "sftp put failed", ex);
        } finally {
            SftpDriverUtil.closeQuietly(connection);
        }
    }
    
    // --- 私有方法 ---
    private String buildTargetPath(String basePath, String path) {
        String normalizedPath = SftpDriverUtil.normalizePath(path);
        if (basePath == null || ".".equals(basePath)) {
            return ".".equals(normalizedPath) ? "/" : (normalizedPath.startsWith("/") ? normalizedPath : "/" + normalizedPath);
        }
        if (".".equals(normalizedPath)) {
            return basePath;
        }
        return SftpDriverUtil.join(basePath, normalizedPath);
    }

    private void deleteDirRecursive(SftpClient sftpClient, String dir) throws IOException {
        for (SftpClient.DirEntry entry : sftpClient.readDir(dir)) {
            if (".".equals(entry.getFilename()) || "..".equals(entry.getFilename())) {
                continue;
            }
            String child = SftpDriverUtil.join(dir, entry.getFilename());
            if (entry.getAttributes().isDirectory()) {
                deleteDirRecursive(sftpClient, child);
            } else {
                sftpClient.remove(child);
            }
        }
        sftpClient.rmdir(dir);
    }
}
