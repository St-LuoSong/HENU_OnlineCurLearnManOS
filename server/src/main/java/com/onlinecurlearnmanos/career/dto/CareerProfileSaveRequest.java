package com.onlinecurlearnmanos.career.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CareerProfileSaveRequest(
        @Size(max = 128, message = "专业名称不能超过 128 个字符")
        String major,

        @Size(max = 32, message = "年级不能超过 32 个字符")
        String gradeLevel,

        @Min(value = 0, message = "每周可投入时间不能小于 0")
        @Max(value = 168, message = "每周可投入时间不能大于 168")
        Integer weeklyHours,

        @Size(max = 12, message = "兴趣标签最多 12 个")
        List<String> interestTags,

        @Size(max = 1000, message = "优势说明不能超过 1000 个字符")
        String strengthNotes,

        @Size(max = 500, message = "期望方向不能超过 500 个字符")
        String careerGoal
) {
}
