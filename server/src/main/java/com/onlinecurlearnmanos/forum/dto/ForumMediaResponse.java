package com.onlinecurlearnmanos.forum.dto;

/** 帖子媒体。前端用 fileId 通过带 JWT 的 Blob 接口取内容，不暴露物理路径。 */
public record ForumMediaResponse(
        Long id,
        Long fileId,
        String originalName,
        String mimeType,
        Long sizeBytes,
        int sortOrder,
        boolean canDelete
) {
}

