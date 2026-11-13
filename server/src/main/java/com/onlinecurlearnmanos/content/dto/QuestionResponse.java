package com.onlinecurlearnmanos.content.dto;

import java.util.List;

public record QuestionResponse(
        Long id,
        Long subjectId,
        String subjectName,
        Long knowledgePointId,
        String knowledgePointName,
        String questionType,
        Integer difficulty,
        String content,
        String correctAnswer,
        String explanation,
        boolean transfer,
        List<QuestionOptionResponse> options
) {
}
