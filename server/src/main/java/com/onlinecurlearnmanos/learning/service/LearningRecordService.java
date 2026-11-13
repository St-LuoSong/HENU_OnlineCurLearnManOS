package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.learning.dto.LearningRecordResponse;
import com.onlinecurlearnmanos.learning.dto.LearningRecordSaveRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface LearningRecordService {

    List<LearningRecordResponse> list(Long userId, LocalDateTime from, LocalDateTime to);

    LearningRecordResponse create(Long userId, LearningRecordSaveRequest request);

    void updateScore(Long recordId, BigDecimal score, Boolean mastered, String remark);

    void delete(Long id, Long userId);
}
