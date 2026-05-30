package com.mpfm.backend.application.driver.webdav;

import com.mpfm.backend.application.driver.base.DriverObject;

/**
 * WebDAV 类型映射：把 PROPFIND 条目映射为统一对象。
 */
public final class WebdavDriverTypes {
    private WebdavDriverTypes() {
    }

    public static DriverObject toObject(WebdavDriverUtil.DavItem item) {
        return new DriverObject(item.path(), item.name(), item.directory() ? "directory" : "file",
                item.directory() ? 0L : item.sizeBytes(), item.mtime(), item.etag());
    }
}
