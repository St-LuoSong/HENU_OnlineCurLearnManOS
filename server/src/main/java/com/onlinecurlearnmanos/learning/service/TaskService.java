package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.learning.dto.TaskResponse;
import com.onlinecurlearnmanos.learning.dto.TaskSaveRequest;

import java.util.List;

public interface TaskService {

    List<TaskResponse> list(Long userId, Long goalId, String status);

    TaskResponse create(Long userId, TaskSaveRequest request);

    TaskResponse update(Long id, Long userId, TaskSaveRequest request);

    TaskResponse complete(Long id, Long userId);

    TaskResponse reopen(Long id, Long userId);

    void delete(Long id, Long userId);
}
