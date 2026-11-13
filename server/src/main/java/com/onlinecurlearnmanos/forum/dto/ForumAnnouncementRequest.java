package com.onlinecurlearnmanos.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 教师重要公示的发布 / 更新请求。
 *
 * <p>{@code classId} 为空表示全局公示：只有管理员可以创建，教师必须指定自己负责的云班级。</p>
 */
public record ForumAnnouncementRequest(
        Long classId,

        @NotBlank(message = "公示标题不能为空")
        @Size(max = 255, message = "公示标题不能超过 255 个字符")
        String title,

        @NotBlank(message = "公示内容不能为空")
        @Size(max = 2000, message = "公示内容不能超过 2000 个字符")
        String content
) {
}

