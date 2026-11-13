package com.onlinecurlearnmanos.support;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.file.storage.FileStorage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试用内存存储，行为与本地磁盘实现保持一致（键不存在时抛 NOT_FOUND）。
 */
public class InMemoryFileStorage implements FileStorage {

    private final Map<String, byte[]> files = new LinkedHashMap<>();

    @Override
    public String provider() {
        return "LOCAL";
    }

    @Override
    public void store(String storedKey, byte[] content) {
        files.put(storedKey, content);
    }

    @Override
    public InputStream openStream(String storedKey) {
        byte[] content = files.get(storedKey);
        if (content == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件内容不存在或已被清理");
        }
        return new ByteArrayInputStream(content);
    }

    @Override
    public boolean exists(String storedKey) {
        return files.containsKey(storedKey);
    }

    @Override
    public void delete(String storedKey) {
        files.remove(storedKey);
    }

    public int size() {
        return files.size();
    }

    public byte[] content(String storedKey) {
        return files.get(storedKey);
    }
}
