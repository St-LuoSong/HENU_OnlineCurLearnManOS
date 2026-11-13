package com.onlinecurlearnmanos.point.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record PointAdjustRequest(
        @NotNull(message = "积分变动值不能为空")
        @Min(value = -100000, message = "积分变动值不能小于 -100000")
        @Max(value = 100000, message = "积分变动值不能大于 100000")
        Integer amount,

        @Size(max = 500, message = "备注不能超过 500 个字符")
        String remark
) {
}
