package com.mpfm.backend.application.driver.webdav;

import com.mpfm.backend.application.driver.base.DriverCapability;
import com.mpfm.backend.application.driver.base.DriverContext;
import com.mpfm.backend.application.driver.base.DriverLink;
import com.mpfm.backend.application.driver.base.DriverObject;
import com.mpfm.backend.application.driver.base.DriverPutRequest;
import com.mpfm.backend.application.driver.base.StorageDriver;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * WebDAV 协议驱动骨架。
 */
@Component
public class WebdavDriver implements StorageDriver {
    @Override
    public String protocol() {
        return "webdav";
    }

    @Override
    public DriverCapability capability() {
        return DriverCapability.full().withDirectUpload(false);
    }

    @Override
    public void init(DriverContext context) {
        WebdavDriverUtil.DavConnection connection = WebdavDriverUtil.open(context);
        WebdavDriverUtil.propFindSelf(connection, ".");
    }

    @Override
    public List<DriverObject> list(DriverContext context, String dirPath) {
        WebdavDriverUtil.DavConnection connection = WebdavDriverUtil.open(context);
        return WebdavDriverUtil.propFindChildren(connection, dirPath).stream()
                .map(WebdavDriverTypes::toObject)
                .toList();
    }

    @Override
    public DriverObject get(DriverContext context, String path) {
        WebdavDriverUtil.DavConnection connection = WebdavDriverUtil.open(context);
        return WebdavDriverTypes.toObject(WebdavDriverUtil.propFindSelf(connection, path));
    }

    @Override
    public DriverLink link(DriverContext context, String filePath) {
        WebdavDriverUtil.DavConnection connection = WebdavDriverUtil.open(context);
        URI uri = WebdavDriverUtil.resolve(connection, filePath);
        Map<String, String> headers = connection.authorization() == null
                ? Map.of()
                : Map.of("Authorization", connection.authorization());
        return new DriverLink(uri.toString(), headers, null);
    }

    @Override
    public void makeDir(DriverContext context, String parentDirPath, String dirName) {
        WebdavDriverUtil.DavConnection connection = WebdavDriverUtil.open(context);
        String target = WebdavDriverUtil.join(parentDirPath, dirName);
        WebdavDriverUtil.send(connection, "MKCOL", target, null, null, List.of());
    }

    @Override
    public void move(DriverContext context, String srcPath, String dstDirPath) {
        WebdavDriverUtil.DavConnection connection = WebdavDriverUtil.open(context);
        String filename = srcPath.substring(srcPath.lastIndexOf('/') + 1);
        String target = WebdavDriverUtil.join(dstDirPath, filename);
        String destination = WebdavDriverUtil.resolve(connection, target).toString();
        WebdavDriverUtil.send(connection, "MOVE", srcPath, null, null,
                List.of(new WebdavDriverUtil.Header("Destination", destination),
                        new WebdavDriverUtil.Header("Overwrite", "T")));
    }

    @Override
    public void rename(DriverContext context, String srcPath, String newName) {
        WebdavDriverUtil.DavConnection connection = WebdavDriverUtil.open(context);
        String parent = srcPath.contains("/") ? srcPath.substring(0, srcPath.lastIndexOf('/')) : "/";
        String target = WebdavDriverUtil.join(parent.equals("") ? "/" : parent, newName);
        String destination = WebdavDriverUtil.resolve(connection, target).toString();
        WebdavDriverUtil.send(connection, "MOVE", srcPath, null, null,
                List.of(new WebdavDriverUtil.Header("Destination", destination),
                        new WebdavDriverUtil.Header("Overwrite", "T")));
    }

    @Override
    public void copy(DriverContext context, String srcPath, String dstDirPath) {
        WebdavDriverUtil.DavConnection connection = WebdavDriverUtil.open(context);
        String filename = srcPath.substring(srcPath.lastIndexOf('/') + 1);
        String target = WebdavDriverUtil.join(dstDirPath, filename);
        String destination = WebdavDriverUtil.resolve(connection, target).toString();
        WebdavDriverUtil.send(connection, "COPY", srcPath, null, null,
                List.of(new WebdavDriverUtil.Header("Destination", destination),
                        new WebdavDriverUtil.Header("Overwrite", "T")));
    }

    @Override
    public void remove(DriverContext context, String path) {
        WebdavDriverUtil.DavConnection connection = WebdavDriverUtil.open(context);
        WebdavDriverUtil.send(connection, "DELETE", path, null, null, List.of());
    }

    @Override
    public DriverObject put(DriverContext context, DriverPutRequest request) {
        WebdavDriverUtil.DavConnection connection = WebdavDriverUtil.open(context);
        String target = WebdavDriverUtil.join(request.dstDirPath(), request.fileName());
        byte[] payload = request.content() == null ? new byte[0] : request.content();
        WebdavDriverUtil.send(connection, "PUT", target, null, payload,
                List.of(new WebdavDriverUtil.Header("Content-Type", "application/octet-stream")));
        return get(context, target);
    }
}
