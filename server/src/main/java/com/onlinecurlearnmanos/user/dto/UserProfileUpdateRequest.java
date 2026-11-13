package com.onlinecurlearnmanos.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名不能超过 64 个字符")
        String displayName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱不能超过 128 个字符")
        String email,

        @Size(max = 512, message = "头像地址不能超过 512 个字符")
        String avatarUrl,

        @Size(max = 128, message = "院系不能超过 128 个字符")
        String department,

        @Size(max = 128, message = "专业不能超过 128 个字符")
        String major,

        @Size(max = 32, message = "年级不能超过 32 个字符")
        String gradeLevel,

        @Size(max = 32, message = "手机号不能超过 32 个字符")
        String phone,

        @Size(max = 500, message = "个人简介不能超过 500 个字符")
        String bio,

        @Size(max = 64, message = "职称不能超过 64 个字符")
        String teacherTitle,

        @Size(max = 255, message = "研究方向不能超过 255 个字符")
        String researchArea,

        Boolean notifyEmail,

        Boolean notifyForumReply
) {
}
