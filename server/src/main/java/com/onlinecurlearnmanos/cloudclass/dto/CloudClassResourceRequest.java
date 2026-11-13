package com.onlinecurlearnmanos.cloudclass.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloudClassResourceRequest(
        @NotBlank(message = "资源标题不能为空")
        @Size(max = 255, message = "资源标题不能超过 255 个字符")
        String title,

        @NotBlank(message = "资源类型不能为空")
        @Size(max = 32, message = "资源类型不能超过 32 个字符")
        String resourceType,

        String content,

        @Size(max = 512, message = "外部地址不能超过 512 个字符")
        String externalUrl
) {
}
