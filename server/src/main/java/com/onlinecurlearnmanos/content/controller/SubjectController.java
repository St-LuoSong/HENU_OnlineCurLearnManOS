package com.onlinecurlearnmanos.content.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.content.dto.KnowledgePointResponse;
import com.onlinecurlearnmanos.content.dto.SubjectResponse;
import com.onlinecurlearnmanos.content.service.KnowledgePointService;
import com.onlinecurlearnmanos.content.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;
    private final KnowledgePointService knowledgePointService;

    @GetMapping
    public ApiResponse<List<SubjectResponse>> list() {
        return ApiResponse.success(subjectService.listSubjects());
    }

    @GetMapping("/{subjectId}/knowledge-points")
    public ApiResponse<List<KnowledgePointResponse>> listKnowledgePoints(
            @PathVariable Long subjectId
    ) {
        return ApiResponse.success(knowledgePointService.list(subjectId, null));
    }
}
