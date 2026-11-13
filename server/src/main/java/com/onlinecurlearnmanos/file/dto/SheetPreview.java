package com.onlinecurlearnmanos.file.dto;

import java.util.List;

/**
 * 单个工作表的预览数据。{@code truncated} 表示因行数或单元格上限被截断。
 */
public record SheetPreview(
        String name,
        List<List<String>> rows,
        boolean truncated
) {
}
