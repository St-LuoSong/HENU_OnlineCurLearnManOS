package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.learning.dto.GoalResponse;
import com.onlinecurlearnmanos.learning.dto.GoalSaveRequest;
import com.onlinecurlearnmanos.learning.dto.GoalGenerateRequest;
import com.onlinecurlearnmanos.learning.dto.GoalSuggestionResponse;

import java.util.List;

public interface GoalService {

    List<GoalResponse> list(Long userId);

    GoalResponse create(Long userId, GoalSaveRequest request);

    GoalResponse update(Long id, Long userId, GoalSaveRequest request);

    GoalResponse complete(Long id, Long userId);

    List<GoalSuggestionResponse> generate(Long userId, GoalGenerateRequest request);

    void delete(Long id, Long userId);
}
