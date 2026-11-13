package com.onlinecurlearnmanos.file.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 文件存储与预览相关配置。允许的扩展名、单文件大小与预览上限都可以通过环境变量覆盖。
 */
@Component
public class FileStorageProperties {

    private final String storageRoot;
    private final long maxSizeBytes;
    private final Set<String> allowedExtensions;
    private final long imageMaxSizeBytes;
    private final Set<String> imageAllowedExtensions;
    private final long imageMaxPixels;
    private final int previewMaxRows;
    private final int previewMaxCells;
    private final int previewMaxChars;

    public FileStorageProperties(
            @Value("${app.file.storage-root:./data/files}") String storageRoot,
            @Value("${app.file.max-size-bytes:20971520}") long maxSizeBytes,
            @Value("${app.file.allowed-extensions:xls,xlsx,docx,pdf,md,txt}")
            String allowedExtensions,
            @Value("${app.file.image-max-size-bytes:5242880}") long imageMaxSizeBytes,
            @Value("${app.file.image-allowed-extensions:jpg,jpeg,png,webp,gif}")
            String imageAllowedExtensions,
            @Value("${app.file.image-max-pixels:20000000}") long imageMaxPixels,
            @Value("${app.file.preview-max-rows:200}") int previewMaxRows,
            @Value("${app.file.preview-max-cells:2000}") int previewMaxCells,
            @Value("${app.file.preview-max-chars:200000}") int previewMaxChars
    ) {
        this.storageRoot = storageRoot;
        this.maxSizeBytes = maxSizeBytes;
        this.allowedExtensions = parseExtensions(allowedExtensions);
        this.imageMaxSizeBytes = imageMaxSizeBytes;
        this.imageAllowedExtensions = parseExtensions(imageAllowedExtensions);
        this.imageMaxPixels = imageMaxPixels;
        this.previewMaxRows = previewMaxRows;
        this.previewMaxCells = previewMaxCells;
        this.previewMaxChars = previewMaxChars;
    }

    private Set<String> parseExtensions(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of("xls", "xlsx", "docx", "pdf", "md", "txt");
        }
        Set<String> parsed = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT).replace(".", ""))
                .forEach(parsed::add);
        return Set.copyOf(parsed);
    }

    public String getStorageRoot() {
        return storageRoot;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public boolean isAllowedExtension(String extension) {
        return extension != null
                && allowedExtensions.contains(extension.toLowerCase(Locale.ROOT));
    }

    /** 图片用途的独立体积上限，默认 5 MB，避免手机原图直接撑爆存储。 */
    public long getImageMaxSizeBytes() {
        return imageMaxSizeBytes;
    }

    public Set<String> getImageAllowedExtensions() {
        return imageAllowedExtensions;
    }

    public boolean isAllowedImageExtension(String extension) {
        return extension != null
                && imageAllowedExtensions.contains(extension.toLowerCase(Locale.ROOT));
    }

    /** 单张图片的像素上限，用于阻断“小体积、大画布”的解压炸弹。 */
    public long getImageMaxPixels() {
        return imageMaxPixels;
    }

    public int getPreviewMaxRows() {
        return previewMaxRows;
    }

    public int getPreviewMaxCells() {
        return previewMaxCells;
    }

    public int getPreviewMaxChars() {
        return previewMaxChars;
    }
}
