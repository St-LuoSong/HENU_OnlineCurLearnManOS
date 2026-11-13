package com.onlinecurlearnmanos.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DashboardNoteSaveRequest(
        @NotNull(message = "便签日期不能为空")
        LocalDate noteDate,

        @NotBlank(message = "便签内容不能为空")
        @Size(max = 500, message = "便签内容不能超过 500 个字符")
        String content,

        @Pattern(regexp = "DEFAULT|IMPORTANT|RELAX", message = "便签标记只能是 DEFAULT、IMPORTANT 或 RELAX")
        String tone
) {
}

