package com.onlinecurlearnmanos.cloudclass.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CloudClassCreateRequest(
        @NotNull(message = "请选择课程")
        Long subjectId,

        @NotBlank(message = "班级名称不能为空")
        @Size(max = 128, message = "班级名称不能超过 128 个字符")
        String name,

        @Size(max = 1000, message = "班级说明不能超过 1000 个字符")
        String description,

        @Size(max = 16, message = "学年不能超过 16 个字符")
        String academicYear,

        @Size(max = 16, message = "学期不能超过 16 个字符")
        String semester,

        @Min(value = 1, message = "人数上限不能小于 1")
        Integer maxMembers
) {
}
