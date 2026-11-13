package com.onlinecurlearnmanos.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员内容处置请求。{@code reportId} 为空表示主动巡查（patrol），
 * 非空表示针对某条举报的处理，二者都会写入治理日志。
 */
public record ForumModerationRequest(
        @NotBlank(message = "处置对象类型不能为空")
        String targetType,

        @NotNull(message = "处置对象不能为空")
        Long targetId,

        @NotBlank(message = "请选择处置动作")
        String action,

        @NotBlank(message = "必须填写处置理由")
        @Size(max = 1000, message = "处置理由不能超过 1000 个字符")
        String note,

        Long reportId,

        @Size(max = 255, message = "标题不能超过 255 个字符")
        String title,

        @Size(max = 20000, message = "内容不能超过 20000 个字符")
        String content
) {
}
