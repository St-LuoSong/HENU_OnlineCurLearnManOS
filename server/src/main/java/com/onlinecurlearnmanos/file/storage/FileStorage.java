package com.onlinecurlearnmanos.file.storage;

import java.io.InputStream;

/**
 * 文件存储抽象。首期只有本地磁盘实现，业务侧只感知 {@code storedKey}，
 * 后续接入 MinIO / OSS 时新增实现即可，不需要改业务表。
 */
public interface FileStorage {

    String provider();

    void store(String storedKey, byte[] content);

    InputStream openStream(String storedKey);

    boolean exists(String storedKey);

    void delete(String storedKey);
}
