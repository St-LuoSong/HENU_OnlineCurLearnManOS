package com.onlinecurlearnmanos.content.dto;

import java.util.List;

public record KnowledgePointResponse(
        Long id,
        Long subjectId,
        String subjectCode,
        String subjectName,
        String code,
        String name,
        String title,
        String content,
        String sourceType,
        String sourceUrl,
        List<String> keyPoints,
        List<String> commonMistakes,
        List<String> intuitionPumps
) {
}
