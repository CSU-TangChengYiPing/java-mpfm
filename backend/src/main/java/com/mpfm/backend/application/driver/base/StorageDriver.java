package com.mpfm.backend.application.driver.base;

import java.util.List;

/**
 * 存储驱动统一契约
 */
public interface StorageDriver {
    String protocol();

    DriverCapability capability();

    void init(DriverContext context);

    List<DriverObject> list(DriverContext context, String dirPath);

    DriverObject get(DriverContext context, String path);

    DriverLink link(DriverContext context, String filePath);

    void makeDir(DriverContext context, String parentDirPath, String dirName);

    void move(DriverContext context, String srcPath, String dstDirPath);

    void rename(DriverContext context, String srcPath, String newName);

    void copy(DriverContext context, String srcPath, String dstDirPath);

    void remove(DriverContext context, String path);

    DriverObject put(DriverContext context, DriverPutRequest request);
}

