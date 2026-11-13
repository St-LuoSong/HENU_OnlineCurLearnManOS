package com.onlinecurlearnmanos.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ForumReportRequest(
        @NotBlank(message = "举报对象类型不能为空")
        String targetType,

        @NotNull(message = "举报对象不能为空")
        Long targetId,

        @NotBlank(message = "请选择举报原因")
        @Size(max = 32, message = "举报原因过长")
        String reason,

        @Size(max = 1000, message = "补充说明不能超过 1000 个字符")
        String detail
) {
}
