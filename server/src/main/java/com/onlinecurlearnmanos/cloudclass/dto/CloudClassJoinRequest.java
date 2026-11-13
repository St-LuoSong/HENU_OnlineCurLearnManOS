package com.onlinecurlearnmanos.cloudclass.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloudClassJoinRequest(
        @NotBlank(message = "班级码不能为空")
        @Size(min = 6, max = 6, message = "班级码必须为 6 位")
        String classCode
) {
}
