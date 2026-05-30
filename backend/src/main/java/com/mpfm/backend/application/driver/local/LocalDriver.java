package com.mpfm.backend.application.driver.local;

import com.mpfm.backend.application.driver.base.DriverCapability;
import com.mpfm.backend.application.driver.base.DriverContext;
import com.mpfm.backend.application.driver.base.DriverLink;
import com.mpfm.backend.application.driver.base.DriverObject;
import com.mpfm.backend.application.driver.base.DriverPutRequest;
import com.mpfm.backend.application.driver.base.StorageDriver;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Local 协议驱动：用于本地文件系统挂载读写。
 */
@Component
public class LocalDriver implements StorageDriver {
    @Override
    public String protocol() {
        return "local";
    }

    @Override
    public DriverCapability capability() {
        return DriverCapability.full().withDirectUpload(false);
    }

    @Override
    public void init(DriverContext context) {
        Path root = Path.of(context.mount().getPhysicalRoot());
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "local driver init failed", ex);
        }
    }

    @Override
    public List<DriverObject> list(DriverContext context, String dirPath) {
        Path target = LocalDriverUtil.resolveUnderMount(context, dirPath);
        if (!Files.exists(target)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "path not found");
        }
        try (Stream<Path> stream = Files.list(target)) {
            return stream.map(LocalDriverTypes::toObject).toList();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "local list failed", ex);
        }
    }

    @Override
    public DriverObject get(DriverContext context, String path) {
        Path target = LocalDriverUtil.resolveUnderMount(context, path);
        if (!Files.exists(target)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "path not found");
        }
        return LocalDriverTypes.toObject(target);
    }

    @Override
    public DriverLink link(DriverContext context, String filePath) {
        Path target = LocalDriverUtil.resolveUnderMount(context, filePath);
        return new DriverLink(target.toUri().toString(), Map.of(), null);
    }

    @Override
    public void makeDir(DriverContext context, String parentDirPath, String dirName) {
        Path parent = LocalDriverUtil.resolveUnderMount(context, parentDirPath);
        try {
            Files.createDirectories(parent.resolve(dirName));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "local mkdir failed", ex);
        }
    }

    @Override
    public void move(DriverContext context, String srcPath, String dstDirPath) {
        Path src = LocalDriverUtil.resolveUnderMount(context, srcPath);
        Path dst = LocalDriverUtil.resolveUnderMount(context, dstDirPath).resolve(src.getFileName());
        LocalDriverUtil.move(src, dst);
    }

    @Override
    public void rename(DriverContext context, String srcPath, String newName) {
        Path src = LocalDriverUtil.resolveUnderMount(context, srcPath);
        LocalDriverUtil.move(src, src.getParent().resolve(newName));
    }

    @Override
    public void copy(DriverContext context, String srcPath, String dstDirPath) {
        Path src = LocalDriverUtil.resolveUnderMount(context, srcPath);
        Path dst = LocalDriverUtil.resolveUnderMount(context, dstDirPath).resolve(src.getFileName());
        LocalDriverUtil.copy(src, dst);
    }

    @Override
    public void remove(DriverContext context, String path) {
        LocalDriverUtil.delete(LocalDriverUtil.resolveUnderMount(context, path));
    }

    @Override
    public DriverObject put(DriverContext context, DriverPutRequest request) {
        Path dstDir = LocalDriverUtil.resolveUnderMount(context, request.dstDirPath());
        Path target = dstDir.resolve(request.fileName());
        LocalDriverUtil.write(target, request.content(), request.overwrite());
        return LocalDriverTypes.toObject(target);
    }
}
