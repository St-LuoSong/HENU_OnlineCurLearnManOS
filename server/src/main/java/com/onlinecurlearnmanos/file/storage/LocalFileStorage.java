package com.onlinecurlearnmanos.file.storage;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.file.config.FileStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * 本地磁盘存储。所有路径都由随机 {@code storedKey} 推导，并在解析后重新校验
 * 是否仍位于配置的存储根目录内，双重防止路径穿越。
 */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
    private static final String PROVIDER = "LOCAL";

    private final Path root;

    public LocalFileStorage(FileStorageProperties properties) {
        this.root = Paths.get(properties.getStorageRoot()).toAbsolutePath().normalize();
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public void store(String storedKey, byte[] content) {
        Path target = resolve(storedKey);
        try {
            Files.createDirectories(root);
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(
                    temporary,
                    content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("文件写入失败", exception);
        }
    }

    @Override
    public InputStream openStream(String storedKey) {
        Path target = resolve(storedKey);
        if (!Files.isReadable(target)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件内容不存在或已被清理");
        }
        try {
            return Files.newInputStream(target, StandardOpenOption.READ);
        } catch (IOException exception) {
            throw new UncheckedIOException("文件读取失败", exception);
        }
    }

    @Override
    public boolean exists(String storedKey) {
        return StoredKeys.isSafe(storedKey) && Files.isReadable(resolve(storedKey));
    }

    @Override
    public void delete(String storedKey) {
        if (!StoredKeys.isSafe(storedKey)) {
            log.warn("Skip deleting unsafe stored key");
            return;
        }
        try {
            Files.deleteIfExists(resolve(storedKey));
        } catch (IOException exception) {
            log.warn("Failed to delete stored file {}", storedKey, exception);
        }
    }

    /**
     * 只接受随机生成的存储键；解析后再次确认仍在存储根目录内。
     */
    private Path resolve(String storedKey) {
        if (!StoredKeys.isSafe(storedKey)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法的文件存储键");
        }
        Path resolved = root.resolve(storedKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法的文件存储键");
        }
        return resolved;
    }
}
