package com.onlinecurlearnmanos.file.service;

/**
 * 通过全部校验的上传文件。{@code content} 已经完整读入内存（上限由配置控制）。
 */
public record ValidatedFile(
        String originalName,
        String extension,
        String mimeType,
        long sizeBytes,
        String sha256,
        byte[] content
) {
}
