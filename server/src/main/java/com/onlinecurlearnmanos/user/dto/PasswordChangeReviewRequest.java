package com.onlinecurlearnmanos.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PasswordChangeReviewRequest(
        @NotNull(message = "请选择审批结果")
        Boolean approved,

        @Size(max = 500, message = "审批说明不能超过 500 个字符")
        String note
) {
}
