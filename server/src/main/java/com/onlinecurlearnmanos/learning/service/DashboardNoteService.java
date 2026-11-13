package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.learning.dto.DashboardNoteResponse;
import com.onlinecurlearnmanos.learning.dto.DashboardNoteSaveRequest;

import java.time.LocalDate;
import java.util.List;

/** 学习概览日历便签：只归属创建者，不与目标 / 任务状态混用。 */
public interface DashboardNoteService {

    List<DashboardNoteResponse> list(Long userId, LocalDate from, LocalDate to);

    DashboardNoteResponse create(Long userId, DashboardNoteSaveRequest request);

    DashboardNoteResponse update(Long id, Long userId, DashboardNoteSaveRequest request);

    void delete(Long id, Long userId);
}

