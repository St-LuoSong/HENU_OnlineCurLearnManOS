package com.onlinecurlearnmanos.content.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.content.dto.QuestionResponse;
import com.onlinecurlearnmanos.content.dto.QuestionSaveRequest;
import com.onlinecurlearnmanos.content.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class QuestionController {

    private final QuestionService questionService;

    @GetMapping
    public ApiResponse<List<QuestionResponse>> list(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long knowledgePointId,
            @RequestParam(required = false) String questionType
    ) {
        return ApiResponse.success(questionService.list(subjectId, knowledgePointId, questionType));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionResponse> get(@PathVariable Long id) {
        return ApiResponse.success(questionService.get(id));
    }

    @PostMapping
    public ApiResponse<QuestionResponse> create(
            @Valid @RequestBody QuestionSaveRequest request
    ) {
        return ApiResponse.success(questionService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<QuestionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody QuestionSaveRequest request
    ) {
        return ApiResponse.success(questionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return ApiResponse.success(null);
    }
}
