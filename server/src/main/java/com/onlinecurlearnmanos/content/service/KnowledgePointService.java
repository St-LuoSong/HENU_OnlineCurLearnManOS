package com.onlinecurlearnmanos.content.service;

import com.onlinecurlearnmanos.content.dto.KnowledgePointResponse;
import com.onlinecurlearnmanos.content.dto.KnowledgePointSaveRequest;

import java.util.List;

public interface KnowledgePointService {

    List<KnowledgePointResponse> list(Long subjectId, String keyword);

    KnowledgePointResponse get(Long id);

    KnowledgePointResponse create(KnowledgePointSaveRequest request);

    KnowledgePointResponse update(Long id, KnowledgePointSaveRequest request);

    void delete(Long id);
}
