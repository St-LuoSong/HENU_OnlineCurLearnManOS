package com.onlinecurlearnmanos.practice.service;

import com.onlinecurlearnmanos.practice.dto.PracticeMessageRequest;
import com.onlinecurlearnmanos.practice.dto.PracticeSessionCreateRequest;
import com.onlinecurlearnmanos.practice.dto.PracticeSessionDetailResponse;
import com.onlinecurlearnmanos.practice.dto.PracticeSessionResponse;
import com.onlinecurlearnmanos.practice.dto.PracticeTurnResponse;

import java.util.List;

public interface PracticeService {

    List<PracticeSessionResponse> list(Long userId);

    PracticeSessionDetailResponse create(Long userId, PracticeSessionCreateRequest request);

    PracticeSessionDetailResponse detail(Long userId, Long sessionId);

    PracticeTurnResponse sendMessage(Long userId, Long sessionId, PracticeMessageRequest request);

    PracticeTurnResponse startQuestion(Long userId, Long sessionId);

    PracticeTurnResponse requestHint(Long userId, Long sessionId);

    void delete(Long userId, Long sessionId);
}
