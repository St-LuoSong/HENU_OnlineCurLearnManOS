package com.onlinecurlearnmanos.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserDeleteRequest(
        @NotBlank(message = "删除原因不能为空")
        @Size(max = 500, message = "删除原因不能超过 500 个字符")
        String reason,

        @NotBlank(message = "请输入待删除账号的学号 / 工号")
        String confirmLoginId
) {
}
