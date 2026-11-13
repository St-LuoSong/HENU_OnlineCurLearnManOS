package com.onlinecurlearnmanos.admin.dto;

import jakarta.validation.constraints.NotNull;

public record AdminUserStatusRequest(
        @NotNull(message = "账号状态不能为空")
        Boolean enabled
) {
}
