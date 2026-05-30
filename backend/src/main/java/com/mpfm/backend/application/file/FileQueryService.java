package com.mpfm.backend.application.file;

import com.mpfm.backend.application.driver.base.DriverContext;
import com.mpfm.backend.application.driver.base.DriverFactory;
import com.mpfm.backend.application.driver.base.DriverObject;
import com.mpfm.backend.application.driver.base.DriverLink;
import com.mpfm.backend.application.driver.sftp.SftpDriverUtil;
import com.mpfm.backend.application.share.v5.ShareAuthorizationV5Service;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Base64;
import java.util.UUID;
import org.apache.sshd.sftp.client.SftpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class FileQueryService {
    private static final String MESSAGE_PATH_NOT_FOUND = "path not found";
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final Logger LOG = LoggerFactory.getLogger(FileQueryService.class);

    private final FileAccessService accessService;
    private final ShareAuthorizationV5Service shareAuthorizationV5Service;
    private final FileEntryMapper entryMapper;
    private final DriverFactory driverFactory;

    FileQueryService(FileAccessService accessService,
                     ShareAuthorizationV5Service shareAuthorizationV5Service,
                     FileEntryMapper entryMapper,
                     DriverFactory driverFactory) {
        this.accessService = accessService;
        this.shareAuthorizationV5Service = shareAuthorizationV5Service;
        this.entryMapper = entryMapper;
        this.driverFactory = driverFactory;
    }

    List<FileApplicationService.EntryResult> tree(String username, UUID mountId, String path) {
        var context = accessService.requireAccess(username, mountId, path, false, true);
        if (!Files.exists(context.target())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, MESSAGE_PATH_NOT_FOUND);
        }
        try {
            List<Path> walked = Files.walk(context.target())
                    .sorted()
                    .filter(item -> !item.equals(context.target()))
                    .toList();
            List<String> virtualPaths = walked.stream()
                    .map(item -> accessService.toVirtualPath(context.mount(), item, context.shared()))
                    .toList();
            var permissions = shareAuthorizationV5Service.effectiveBatch(username, context.mount().getId(), virtualPaths);
            List<FileApplicationService.EntryResult> items = new ArrayList<>();
            for (Path item : walked) {
                String virtualPath = accessService.toVirtualPath(context.mount(), item, context.shared());
                ShareAuthorizationV5Service.EffectivePermissionResult permission =
                        permissions.getOrDefault(virtualPath,
                                new ShareAuthorizationV5Service.EffectivePermissionResult(virtualPath, false, false, false, List.of(), "role_union"));
                if (context.ownerOrAdmin() || permission.canVisible()) {
                    items.add(toEntryWithPermission(context, item, permission, context.ownerOrAdmin()));
                }
            }
            return items;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "tree failed", ex);
        }
    }

    List<FileApplicationService.EntryResult> list(String username, UUID mountId, String path) {
        long startedAtNs = System.nanoTime();
        var context = accessService.requireAccess(username, mountId, path, false, true);
        long accessCostMs = elapsedMs(startedAtNs);

        long driverStartedNs = System.nanoTime();
        var driver = driverFactory.resolve(context.mount().getType());
        String dirPath = accessService.toRelativePath(context.mount(), context.target());
        DriverContext driverContext = new DriverContext(username, context.mount());
        List<DriverObject> objects = driver.list(driverContext, dirPath).stream()
                .sorted(Comparator.comparing(DriverObject::path))
                .toList();
        long driverCostMs = elapsedMs(driverStartedNs);

        long assembleStartedNs = System.nanoTime();
        List<String> virtualPaths = objects.stream()
                .map(item -> accessService.toVirtualPath(context.mount(), item.path(), context.shared()))
                .toList();
        var permissions = shareAuthorizationV5Service.effectiveBatch(username, context.mount().getId(), virtualPaths);
        List<FileApplicationService.EntryResult> results = objects.stream()
                .map(item -> mapListEntry(context, item, permissions))
                .filter(item -> item != null)
                .toList();
        long assembleCostMs = elapsedMs(assembleStartedNs);
        long totalCostMs = elapsedMs(startedAtNs);
        LOG.info("file-list-prof username={} mountId={} mountType={} dirPath={} authCostMs={} driverCostMs={} remoteRttCostMs={} assembleCostMs={} totalCostMs={} rawCount={} resultCount={}",
                username,
                context.mount().getId(),
                context.mount().getType(),
                dirPath,
                accessCostMs,
                driverCostMs,
                driverCostMs,
                assembleCostMs,
                totalCostMs,
                objects.size(),
                results.size());
        return results;
    }

    private long elapsedMs(long startedAtNs) {
        return (System.nanoTime() - startedAtNs) / 1_000_000L;
    }

    FileApplicationService.EntryResult stat(String username, UUID mountId, String path) {
        var context = accessService.requireAccess(username, mountId, path, false, true);
        var driver = driverFactory.resolve(context.mount().getType());
        String relativePath = accessService.toRelativePath(context.mount(), context.target());
        DriverContext driverContext = new DriverContext(username, context.mount());
        DriverObject target = driver.get(driverContext, relativePath);
        String permissionPath = accessService.toVirtualPath(context.mount(), target.path(), context.shared());
        ShareAuthorizationV5Service.EffectivePermissionResult permission =
                shareAuthorizationV5Service.effective(username, context.mount().getId(), permissionPath);
        if (!context.ownerOrAdmin() && permission.canVisible() && !permission.canRead()) {
            return toMinimalEntryByDriver(context, target, permissionPath);
        }
        return toEntryWithPermission(context, target, permission, context.ownerOrAdmin(), permissionPath);
    }

    String readFile(String username, UUID mountId, String path) {
        var context = accessService.requireAccess(username, mountId, path, false, true);
        if (isSftpMount(context.mount().getType())) {
            byte[] bytes = readSftpBytes(username, context.mount(), path);
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (!isLocalMount(context.mount().getType())) {
            byte[] bytes = readRemoteBytes(username, context.mount(), context.target().toString());
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (!Files.exists(context.target())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, MESSAGE_PATH_NOT_FOUND);
        }
        try {
            return Files.readString(context.target(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read failed", ex);
        }
    }

    byte[] readFileBytes(String username, UUID mountId, String path) {
        var context = accessService.requireAccess(username, mountId, path, false, true);
        if (isSftpMount(context.mount().getType())) {
            return readSftpBytes(username, context.mount(), path);
        }
        if (!isLocalMount(context.mount().getType())) {
            return readRemoteBytes(username, context.mount(), context.target().toString());
        }
        if (!Files.exists(context.target())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, MESSAGE_PATH_NOT_FOUND);
        }
        try {
            return Files.readAllBytes(context.target());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read bytes failed", ex);
        }
    }

    FileApplicationService.EntryResult symlinkResolve(String username, UUID mountId, String path) {
        var context = accessService.requireAccess(username, mountId, path, false, true);
        if (!Files.exists(context.target())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, MESSAGE_PATH_NOT_FOUND);
        }
        try {
            Path resolved = context.target().toRealPath();
            Path root = Path.of(context.mount().getPhysicalRoot()).normalize();
            return buildResolvedEntry(context, resolved, root);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.CAPABILITY_NOT_SUPPORTED, "symlink resolve unsupported", ex);
        }
    }

    private FileApplicationService.EntryResult buildResolvedEntry(FileAccessService.AccessContext context, Path resolved, Path root) {
        if (!resolved.startsWith(root)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "resolved path out of mount");
        }
        return entryMapper.toEntry(context.mount(), resolved, context.shared());
    }

    private FileApplicationService.EntryResult mapListEntry(FileAccessService.AccessContext context,
                                                            DriverObject item,
                                                            java.util.Map<String, ShareAuthorizationV5Service.EffectivePermissionResult> permissions) {
        String relativePath = accessService.toVirtualPath(context.mount(), item.path(), context.shared());
        ShareAuthorizationV5Service.EffectivePermissionResult permission =
                permissions.getOrDefault(relativePath,
                        new ShareAuthorizationV5Service.EffectivePermissionResult(relativePath, false, false, false, List.of(), "role_union"));
        if (!context.ownerOrAdmin() && !permission.canVisible()) {
            return null;
        }
        if (!context.ownerOrAdmin() && permission.canVisible() && !permission.canRead()) {
            return toMinimalEntryByDriver(context, item, relativePath);
        }
        return toEntryWithPermission(context, item, permission, context.ownerOrAdmin(), relativePath);
    }

    private FileApplicationService.EntryResult toEntryWithPermission(FileAccessService.AccessContext context,
                                                                     Path item,
                                                                     ShareAuthorizationV5Service.EffectivePermissionResult permission,
                                                                     boolean ownerOrAdmin) {
        boolean visible = ownerOrAdmin || permission.canVisible();
        boolean readable = ownerOrAdmin || permission.canRead();
        boolean writable = ownerOrAdmin || permission.canWrite();
        return entryMapper.toEntry(context.mount(), item, context.shared(), visible, readable, writable);
    }

    private FileApplicationService.EntryResult toEntryWithPermission(FileAccessService.AccessContext context,
                                                                     DriverObject item,
                                                                     ShareAuthorizationV5Service.EffectivePermissionResult permission,
                                                                     boolean ownerOrAdmin,
                                                                     String virtualPath) {
        boolean visible = ownerOrAdmin || permission.canVisible();
        boolean readable = ownerOrAdmin || permission.canRead();
        boolean writable = ownerOrAdmin || permission.canWrite();
        String version = canonicalVersion(item);
        return new FileApplicationService.EntryResult(
                virtualPath,
                item.name(),
                item.type(),
                item.sizeBytes(),
                item.mtime(),
                null,
                visible,
                readable,
                writable,
                item.etag() == null ? "" : item.etag(),
                version
        );
    }

    private String canonicalVersion(DriverObject item) {
        String etag = item.etag();
        if (etag != null && etag.length() >= 2 && etag.startsWith("\"") && etag.endsWith("\"")) {
            String encoded = etag.substring(1, etag.length() - 1);
            try {
                return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // 远端驱动若返回非 base64 ETag，则保留驱动自带的时间戳口径。
            }
        }
        return item.mtime() + ":" + item.sizeBytes();
    }

    private FileApplicationService.EntryResult toMinimalEntryByDriver(FileAccessService.AccessContext context,
                                                                      DriverObject item,
                                                                      String virtualPath) {
        return new FileApplicationService.EntryResult(
                virtualPath,
                item.name(),
                item.type(),
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

    private boolean isSftpMount(String mountType) {
        return "sftp".equalsIgnoreCase(mountType);
    }

    private boolean isLocalMount(String mountType) {
        return "local".equalsIgnoreCase(mountType);
    }

    private byte[] readRemoteBytes(String username,
                                   com.mpfm.backend.infrastructure.persistence.entity.MountEntity mount,
                                   String relPath) {
        DriverContext driverContext = new DriverContext(username, mount);
        DriverLink link = driverFactory.resolve(mount.getType()).link(driverContext, relPath);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(link.url()).toURL().openConnection();
            connection.setRequestMethod("GET");
            if (link.headers() != null) {
                link.headers().forEach(connection::setRequestProperty);
            }
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, MESSAGE_PATH_NOT_FOUND);
            }
            if (status >= 400) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read remote bytes failed, status=" + status);
            }
            try (InputStream input = connection.getInputStream()) {
                return input.readAllBytes();
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read remote bytes failed", ex);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private byte[] readSftpBytes(String username, com.mpfm.backend.infrastructure.persistence.entity.MountEntity mount, String relPath) {
        SftpDriverUtil.SftpConnection connection = SftpDriverUtil.open(new DriverContext(username, mount));
        try {
            String target = toSftpTargetPath(connection.basePath(), relPath);
            SftpClient.Attributes attrs = connection.sftpClient().stat(target);
            if (attrs.isDirectory()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, MESSAGE_PATH_NOT_FOUND);
            }
            long totalSize = attrs.getSize();
            if (totalSize <= 0L) {
                return new byte[0];
            }
            try (SftpClient.CloseableHandle handle = connection.sftpClient().open(target, SftpClient.OpenMode.Read)) {
                byte[] data = new byte[Math.toIntExact(totalSize)];
                byte[] chunk = new byte[BUFFER_SIZE];
                long offset = 0L;
                int writePos = 0;
                while (offset < totalSize) {
                    int expected = (int) Math.min(chunk.length, totalSize - offset);
                    int read = connection.sftpClient().read(handle, offset, chunk, 0, expected);
                    if (read <= 0) {
                        break;
                    }
                    System.arraycopy(chunk, 0, data, writePos, read);
                    writePos += read;
                    offset += read;
                }
                if (writePos == data.length) {
                    return data;
                }
                byte[] resized = new byte[writePos];
                System.arraycopy(data, 0, resized, 0, writePos);
                return resized;
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            SftpDriverUtil.invalidate(new DriverContext(username, mount));
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read bytes failed", ex);
        } finally {
            SftpDriverUtil.closeQuietly(connection);
        }
    }

    private String toSftpTargetPath(String basePath, String relPath) {
        String normalizedRel = SftpDriverUtil.normalizePath(relPath);
        if (basePath == null || ".".equals(basePath)) {
            return ".".equals(normalizedRel) ? "/" : (normalizedRel.startsWith("/") ? normalizedRel : "/" + normalizedRel);
        }
        if (".".equals(normalizedRel)) {
            return basePath;
        }
        return SftpDriverUtil.join(basePath, normalizedRel);
    }
}


