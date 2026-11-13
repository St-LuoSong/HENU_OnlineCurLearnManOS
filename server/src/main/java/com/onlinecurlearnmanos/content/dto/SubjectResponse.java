package com.onlinecurlearnmanos.content.dto;

public record SubjectResponse(
        Long id,
        String code,
        String name,
        String icon,
        String color,
        long knowledgePointCount,
        long questionCount
) {
}
