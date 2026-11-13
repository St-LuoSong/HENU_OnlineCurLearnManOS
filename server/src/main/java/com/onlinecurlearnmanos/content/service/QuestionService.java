package com.onlinecurlearnmanos.content.service;

import com.onlinecurlearnmanos.content.dto.QuestionResponse;
import com.onlinecurlearnmanos.content.dto.QuestionSaveRequest;

import java.util.List;

public interface QuestionService {

    List<QuestionResponse> list(Long subjectId, Long knowledgePointId, String questionType);

    QuestionResponse get(Long id);

    QuestionResponse create(QuestionSaveRequest request);

    QuestionResponse update(Long id, QuestionSaveRequest request);

    void delete(Long id);
}
