package com.onlinecurlearnmanos.file.dto;

import jakarta.validation.constraints.NotNull;

public record AttachmentCreateRequest(
        @NotNull(message = "请选择要关联的文件")
        Long fileId,

        Integer sortOrder
) {
}
