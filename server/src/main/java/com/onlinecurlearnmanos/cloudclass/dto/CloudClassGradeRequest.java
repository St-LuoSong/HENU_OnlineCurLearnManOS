package com.onlinecurlearnmanos.cloudclass.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CloudClassGradeRequest(
        @NotNull(message = "分数不能为空")
        @DecimalMin(value = "0", message = "分数不能小于 0")
        @DecimalMax(value = "100", message = "分数不能大于 100")
        BigDecimal score,

        @Size(max = 1000, message = "评语不能超过 1000 个字符")
        String feedback
) {
}
