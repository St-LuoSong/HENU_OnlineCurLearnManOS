package com.onlinecurlearnmanos.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ForumPostSaveRequest(
        @NotNull(message = "帖子分类不能为空")
        Long categoryId,

        @NotBlank(message = "帖子标题不能为空")
        @Size(max = 255, message = "帖子标题不能超过 255 个字符")
        String title,

        @NotBlank(message = "帖子内容不能为空")
        @Size(max = 20000, message = "帖子内容不能超过 20000 个字符")
        String content,

        /** 帖子图片的文件 ID，按数组顺序决定九宫格展示顺序；为空表示纯文本帖。 */
        @Size(max = 9, message = "一条帖子最多上传 9 张图片")
        List<Long> mediaFileIds
) {
}
