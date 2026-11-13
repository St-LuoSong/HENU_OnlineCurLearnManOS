package com.onlinecurlearnmanos.cloudclass.dto;

import jakarta.validation.constraints.NotNull;

public record CloudClassJoinEnabledRequest(
        @NotNull(message = "请指定是否开放班级码加入")
        Boolean enabled
) {
}
