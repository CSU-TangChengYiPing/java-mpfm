package com.mpfm.backend.application.file;

import com.mpfm.backend.application.driver.base.DriverContext;
import com.mpfm.backend.application.driver.base.DriverFactory;
import com.mpfm.backend.application.driver.base.DriverPutRequest;
import com.mpfm.backend.application.mount.MountQuotaService;
import com.mpfm.backend.application.share.v5.ShareAuthorizationV5Service;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.UUID;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
class FileCommandService {
    private static final String MESSAGE_PATH_NOT_FOUND = "path not found";
    private static final long UNLIMITED_OR_ZERO_QUOTA = 0L;
    private static final String IF_MATCH_WILDCARD = "*";

    private final FileAccessService accessService;
    private final ShareAuthorizationV5Service shareAuthorizationV5Service;
    private final FileEntryMapper entryMapper;
    private final MountQuotaService mountQuotaService;
    private final DriverFactory driverFactory;

    FileCommandService(FileAccessService accessService,
                       ShareAuthorizationV5Service shareAuthorizationV5Service,
                       FileEntryMapper entryMapper,
                       MountQuotaService mountQuotaService,
                       DriverFactory driverFactory) {
        this.accessService = accessService;
        this.shareAuthorizationV5Service = shareAuthorizationV5Service;
        this.entryMapper = entryMapper;
        this.mountQuotaService = mountQuotaService;
        this.driverFactory = driverFactory;
    }

    FileApplicationService.EntryResult writeFile(String username, UUID mountId, String path, String content, String ifMatch) {
        var context = accessService.requireAccess(username, mountId, path, true, true);
        if (!isLocalMount(context.mount().getType())) {
            assertRemoteVersion(username, context, ifMatch);
            var driver = driverFactory.resolve(context.mount().getType());
            Path parent = context.target().getParent();
            String dirPath = accessService.toRelativePath(context.mount(), parent == null ? context.target() : parent);
            String fileName = context.target().getFileName() == null ? "" : context.target().getFileName().toString();
            driver.put(new DriverContext(username, context.mount()),
                    new DriverPutRequest(dirPath, fileName, (content == null ? "" : content).getBytes(StandardCharsets.UTF_8), true));
            return queryByRelativePath(username, context, accessService.toRelativePath(context.mount(), context.target()));
        }
        assertVersion(context.target(), ifMatch);
        Path parent = context.target().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "write failed", ex);
        }
        enforceMountQuota(context.mount(), context.target(), bytesLength(content), false);
        var driver = driverFactory.resolve(context.mount().getType());
        String dirPath = accessService.toRelativePath(context.mount(), parent == null ? context.target() : parent);
        String fileName = context.target().getFileName() == null ? "" : context.target().getFileName().toString();
        driver.put(new DriverContext(username, context.mount()),
                new DriverPutRequest(dirPath, fileName, (content == null ? "" : content).getBytes(StandardCharsets.UTF_8), true));
        return toEntryWithPermission(username, context, context.target());
    }

    FileApplicationService.EntryResult writeFileChunk(String username, UUID mountId, String path, String content, boolean append, String ifMatch) {
        var context = accessService.requireAccess(username, mountId, path, true, true);
        if (!"local".equalsIgnoreCase(context.mount().getType())) {
            if (append) {
                throw new BusinessException(ErrorCode.CAPABILITY_NOT_SUPPORTED, "append not supported for remote mount");
            }
            assertRemoteVersion(username, context, ifMatch);
            var driver = driverFactory.resolve(context.mount().getType());
            Path parent = context.target().getParent();
            String dirPath = accessService.toRelativePath(context.mount(), parent == null ? context.target() : parent);
            String fileName = context.target().getFileName() == null ? "" : context.target().getFileName().toString();
            byte[] payload = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
            driver.put(new DriverContext(username, context.mount()), new DriverPutRequest(dirPath, fileName, payload, true));
            return queryByRelativePath(username, context, accessService.toRelativePath(context.mount(), context.target()));
        }
        assertVersion(context.target(), ifMatch);
        try {
            Path parent = context.target().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            enforceMountQuota(context.mount(), context.target(), bytesLength(content), append);
            Files.writeString(context.target(), content == null ? "" : content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
            return toEntryWithPermission(username, context, context.target());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "write chunk failed", ex);
        }
    }

    void mkdir(String username, UUID mountId, String path, String ifMatch) {
        var context = accessService.requireAccess(username, mountId, path, true, true);
        assertVersionForContext(username, context, ifMatch);
        var driver = driverFactory.resolve(context.mount().getType());
        Path parent = context.target().getParent();
        String parentPath = accessService.toRelativePath(context.mount(), parent == null ? context.target() : parent);
        String dirName = context.target().getFileName() == null ? "." : context.target().getFileName().toString();
        driver.makeDir(new DriverContext(username, context.mount()), parentPath, dirName);
    }

    FileApplicationService.EntryResult rename(String username, UUID mountId, String fromPath, String toName, String ifMatch) {
        var context = accessService.requireAccess(username, mountId, fromPath, true, true);
        assertVersionForContext(username, context, ifMatch);
        if (isLocalMount(context.mount().getType()) && !Files.exists(context.target())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, MESSAGE_PATH_NOT_FOUND);
        }
        Path to = context.target().resolveSibling(toName).normalize();
        if (isLocalMount(context.mount().getType()) && !to.startsWith(Path.of(context.mount().getPhysicalRoot()))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid path");
        }
        var driver = driverFactory.resolve(context.mount().getType());
        String srcPath = accessService.toRelativePath(context.mount(), context.target());
        driver.rename(new DriverContext(username, context.mount()), srcPath, toName);
        if (!isLocalMount(context.mount().getType())) {
            return queryByRelativePath(username, context, accessService.toRelativePath(context.mount(), to));
        }
        return toEntryWithPermission(username, context, to);
    }

    FileApplicationService.EntryResult move(String username, UUID mountId, String fromPath, String toPath, String ifMatch) {
        var fromContext = accessService.requireAccess(username, mountId, fromPath, true, true);
        var toContext = accessService.requireAccess(username, mountId, toPath, true, true);
        assertVersionForContext(username, fromContext, ifMatch);
        var driver = driverFactory.resolve(fromContext.mount().getType());
        String srcPath = accessService.toRelativePath(fromContext.mount(), fromContext.target());
        String dstDirPath = accessService.toRelativePath(toContext.mount(),
                toContext.target().getParent() == null ? toContext.target() : toContext.target().getParent());
        driver.move(new DriverContext(username, fromContext.mount()), srcPath, dstDirPath);
        if (!isLocalMount(fromContext.mount().getType())) {
            return queryByRelativePath(username, toContext, accessService.toRelativePath(toContext.mount(), toContext.target()));
        }
        return toEntryWithPermission(username, fromContext, toContext.target());
    }

    FileApplicationService.EntryResult copy(String username, UUID mountId, String fromPath, String toPath, String ifMatch) {
        var fromContext = accessService.requireAccess(username, mountId, fromPath, true, true);
        var toContext = accessService.requireAccess(username, mountId, toPath, true, true);
        assertVersionForContext(username, fromContext, ifMatch);
        if (isLocalMount(fromContext.mount().getType())) {
            long sourceSize = Files.exists(fromContext.target()) ? fileSize(fromContext.target()) : 0L;
            enforceMountQuota(toContext.mount(), toContext.target(), sourceSize, false);
        }
        var driver = driverFactory.resolve(fromContext.mount().getType());
        String srcPath = accessService.toRelativePath(fromContext.mount(), fromContext.target());
        String dstDirPath = accessService.toRelativePath(toContext.mount(),
                toContext.target().getParent() == null ? toContext.target() : toContext.target().getParent());
        driver.copy(new DriverContext(username, fromContext.mount()), srcPath, dstDirPath);
        if (!isLocalMount(fromContext.mount().getType())) {
            return queryByRelativePath(username, toContext, accessService.toRelativePath(toContext.mount(), toContext.target()));
        }
        return toEntryWithPermission(username, fromContext, toContext.target());
    }

    void delete(String username, UUID mountId, String path, String ifMatch) {
        var context = accessService.requireAccess(username, mountId, path, true, true);
        assertVersionForContext(username, context, ifMatch);
        if (isLocalMount(context.mount().getType()) && !Files.exists(context.target())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, MESSAGE_PATH_NOT_FOUND);
        }
        if (isLocalMount(context.mount().getType()) && !context.ownerOrAdmin() && Files.isDirectory(context.target())) {
            assertDirectoryWritable(username, context);
        }
        var driver = driverFactory.resolve(context.mount().getType());
        String targetPath = accessService.toRelativePath(context.mount(), context.target());
        driver.remove(new DriverContext(username, context.mount()), targetPath);
    }

    private void assertDirectoryWritable(String username, FileAccessService.AccessContext context) {
        List<Path> nested;
        try {
            nested = Files.walk(context.target()).toList();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "delete failed", ex);
        }
        for (Path item : nested) {
            String relativePath = accessService.toVirtualPath(context.mount(), item, context.shared());
            ShareAuthorizationV5Service.EffectivePermissionResult permission =
                    shareAuthorizationV5Service.effective(username, context.mount().getId(), relativePath);
            if (!permission.canWrite()) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, "directory children not writable");
            }
        }
    }

    FileApplicationService.EntryResult writeFileBytes(String username, UUID mountId, String path, byte[] content, String ifMatch) {
        var context = accessService.requireAccess(username, mountId, path, true, true);
        if (!isLocalMount(context.mount().getType())) {
            assertRemoteVersion(username, context, ifMatch);
            byte[] payload = content == null ? new byte[0] : content;
            Path parent = context.target().getParent();
            var driver = driverFactory.resolve(context.mount().getType());
            String dirPath = accessService.toRelativePath(context.mount(), parent == null ? context.target() : parent);
            String fileName = context.target().getFileName() == null ? "" : context.target().getFileName().toString();
            driver.put(new DriverContext(username, context.mount()),
                    new DriverPutRequest(dirPath, fileName, payload, true));
            return queryByRelativePath(username, context, accessService.toRelativePath(context.mount(), context.target()));
        }
        assertVersion(context.target(), ifMatch);
        Path parent = context.target().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "write binary file failed", ex);
        }
        byte[] payload = content == null ? new byte[0] : content;
        enforceMountQuota(context.mount(), context.target(), payload.length, false);
        var driver = driverFactory.resolve(context.mount().getType());
        String dirPath = accessService.toRelativePath(context.mount(), parent == null ? context.target() : parent);
        String fileName = context.target().getFileName() == null ? "" : context.target().getFileName().toString();
        driver.put(new DriverContext(username, context.mount()),
                new DriverPutRequest(dirPath, fileName, payload, true));
        return toEntryWithPermission(username, context, context.target());
    }

    private void enforceMountQuota(com.mpfm.backend.infrastructure.persistence.entity.MountEntity mount,
                                   Path targetPath, long incomingBytes, boolean append) {
        long quota = mountQuotaService.effectiveCapacityBytes(mount);
        if (quota <= UNLIMITED_OR_ZERO_QUOTA) {
            return;
        }
        Path root = Path.of(mount.getPhysicalRoot());
        long currentUsed = calculateUsedBytes(root);
        long existingSize = Files.exists(targetPath) && Files.isRegularFile(targetPath) ? fileSize(targetPath) : 0L;
        long projectedUsed = append ? currentUsed + incomingBytes : currentUsed - existingSize + incomingBytes;
        if (currentUsed > quota && projectedUsed > currentUsed) {
            throw new BusinessException(ErrorCode.CAPABILITY_RESTRICTED, "mount capacity exceeded");
        }
        if (projectedUsed > quota) {
            throw new BusinessException(ErrorCode.CAPABILITY_RESTRICTED, "mount capacity exceeded");
        }
    }

    private long calculateUsedBytes(Path root) {
        if (!Files.exists(root)) {
            return 0L;
        }
        try {
            return Files.walk(root)
                    .filter(Files::isRegularFile)
                    .mapToLong(this::fileSize)
                    .sum();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "calculate mount usage failed", ex);
        }
    }

    private long fileSize(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return attrs.size();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read file size failed", ex);
        }
    }

    private long bytesLength(String content) {
        return (content == null ? "" : content).getBytes(StandardCharsets.UTF_8).length;
    }

    private void assertVersionForContext(String username, FileAccessService.AccessContext context, String ifMatch) {
        if (isLocalMount(context.mount().getType())) {
            assertVersion(context.target(), ifMatch);
            return;
        }
        assertRemoteVersion(username, context, ifMatch);
    }

    private void assertVersion(Path target, String ifMatch) {
        String resolvedPath = target.toString();
        String currentVersion = "MISSING";
        String currentEtag = "MISSING";
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "If-Match header is required");
        }
        if (!Files.exists(target)) {
            if (IF_MATCH_WILDCARD.equals(ifMatch.trim())) {
                return;
            }
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "version conflict");
        }
        BasicFileAttributes attrs = readAttributes(target);
        long size = Files.isDirectory(target) ? 0L : attrs.size();
        currentVersion = attrs.lastModifiedTime().toMillis() + ":" + size;
        currentEtag = "\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(currentVersion.getBytes(StandardCharsets.UTF_8)) + "\"";
        if (ifMatch.equals(currentVersion) || ifMatch.equals(currentEtag) || IF_MATCH_WILDCARD.equals(ifMatch.trim())) {
            return;
        }
        throw new BusinessException(ErrorCode.VERSION_CONFLICT, "version conflict");
    }

    private BasicFileAttributes readAttributes(Path target) {
        try {
            return Files.readAttributes(target, BasicFileAttributes.class);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "read version failed", ex);
        }
    }

    private FileApplicationService.EntryResult toEntryWithPermission(String username,
                                                                     FileAccessService.AccessContext context,
                                                                     Path target) {
        String relativePath = accessService.toVirtualPath(context.mount(), target, context.shared());
        ShareAuthorizationV5Service.EffectivePermissionResult permission =
                shareAuthorizationV5Service.effective(username, context.mount().getId(), relativePath);
        boolean visible = context.ownerOrAdmin() || permission.canVisible();
        boolean readable = context.ownerOrAdmin() || permission.canRead();
        boolean writable = context.ownerOrAdmin() || permission.canWrite();
        return entryMapper.toEntry(context.mount(), target, context.shared(), visible, readable, writable);
    }

    private void assertRemoteVersion(String username, FileAccessService.AccessContext context, String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "If-Match header is required");
        }
        String relativePath = accessService.toRelativePath(context.mount(), context.target());
        String resolvedPath = context.target().toString();
        DriverContext driverContext = new DriverContext(username, context.mount());
        var driver = driverFactory.resolve(context.mount().getType());
        try {
            var object = driver.get(driverContext, relativePath);
            String currentVersion = object.mtime() + ":" + object.sizeBytes();
            String currentEtag = object.etag();
            if (ifMatch.equals(currentVersion) || ifMatch.equals(currentEtag) || IF_MATCH_WILDCARD.equals(ifMatch.trim())) {
                return;
            }
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "version conflict");
        } catch (BusinessException ex) {
            if (IF_MATCH_WILDCARD.equals(ifMatch.trim()) && ex.getCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                return;
            }
            throw ex;
        }
    }

    private FileApplicationService.EntryResult queryByRelativePath(String username,
                                                                   FileAccessService.AccessContext context,
                                                                   String relativePath) {
        var object = driverFactory.resolve(context.mount().getType()).get(new DriverContext(username, context.mount()), relativePath);
        String virtualPath = accessService.toVirtualPath(context.mount(), object.path(), context.shared());
        ShareAuthorizationV5Service.EffectivePermissionResult permission =
                shareAuthorizationV5Service.effective(username, context.mount().getId(), virtualPath);
        boolean visible = context.ownerOrAdmin() || permission.canVisible();
        boolean readable = context.ownerOrAdmin() || permission.canRead();
        boolean writable = context.ownerOrAdmin() || permission.canWrite();
        String version = canonicalVersion(object);
        return new FileApplicationService.EntryResult(
                virtualPath,
                object.name(),
                object.type(),
                object.sizeBytes(),
                object.mtime(),
                null,
                visible,
                readable,
                writable,
                object.etag() == null ? "" : object.etag(),
                version
        );
    }

    private String canonicalVersion(com.mpfm.backend.application.driver.base.DriverObject object) {
        String etag = object.etag();
        if (etag != null && etag.length() >= 2 && etag.startsWith("\"") && etag.endsWith("\"")) {
            String encoded = etag.substring(1, etag.length() - 1);
            try {
                return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // 远端驱动若返回非 base64 ETag，则保留驱动自带的时间戳口径。
            }
        }
        return object.mtime() + ":" + object.sizeBytes();
    }

    private boolean isLocalMount(String mountType) {
        return "local".equalsIgnoreCase(mountType);
    }
}


