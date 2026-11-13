package com.onlinecurlearnmanos.cloudclass.dto;

import jakarta.validation.constraints.NotBlank;

public record CloudClassSubmissionRequest(
        @NotBlank(message = "提交内容不能为空")
        String content
) {
}
