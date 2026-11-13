package com.onlinecurlearnmanos.cloudclass.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloudClassMemberStatusRequest(
        @NotBlank(message = "成员状态不能为空")
        @Size(max = 16, message = "成员状态不能超过 16 个字符")
        String status
) {
}
