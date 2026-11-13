package com.onlinecurlearnmanos.growth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record GrowthCycleCreateRequest(
        @NotBlank(message = "周期名称不能为空")
        @Size(max = 128, message = "周期名称不能超过 128 个字符")
        String title,

        @NotNull(message = "开始日期不能为空")
        LocalDate startDate,

        @NotNull(message = "结束日期不能为空")
        LocalDate endDate,

        List<Long> carryOverGoalIds,

        List<Long> carryOverTaskIds
) {
}
