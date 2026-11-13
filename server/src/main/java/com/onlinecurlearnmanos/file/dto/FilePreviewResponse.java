package com.onlinecurlearnmanos.file.dto;

import java.util.List;

/**
 * 在线预览结果。{@code kind} 决定前端渲染方式：
 * <ul>
 *     <li>{@code PDF}：使用 {@code contentUrl} 内联渲染</li>
 *     <li>{@code TEXT}：使用 {@code text} 纯文本展示</li>
 *     <li>{@code HTML}：使用 {@code html}（服务端已转义并清洗）渲染</li>
 *     <li>{@code SHEET}：使用 {@code sheets} 渲染表格</li>
 * </ul>
 */
public record FilePreviewResponse(
        Long fileId,
        String originalName,
        String extension,
        String kind,
        String contentUrl,
        String text,
        String html,
        List<SheetPreview> sheets,
        int totalSheets,
        boolean truncated
) {

    public static final String KIND_PDF = "PDF";
    public static final String KIND_TEXT = "TEXT";
    public static final String KIND_HTML = "HTML";
    public static final String KIND_SHEET = "SHEET";
}
