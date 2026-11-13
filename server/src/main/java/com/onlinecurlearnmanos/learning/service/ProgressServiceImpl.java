package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.learning.dto.ProgressResponse;
import com.onlinecurlearnmanos.learning.dto.ProgressUpdateRequest;
import com.onlinecurlearnmanos.learning.entity.StudentProgress;
import com.onlinecurlearnmanos.learning.mapper.StudentProgressMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProgressServiceImpl implements ProgressService {

    private final StudentProgressMapper progressMapper;
    private final SubjectMapper subjectMapper;
    private final KnowledgePointMapper knowledgePointMapper;

    @Override
    public List<ProgressResponse> list(Long userId) {
        return progressMapper.selectList(new LambdaQueryWrapper<StudentProgress>()
                        .eq(StudentProgress::getUserId, userId)
                        .orderByDesc(StudentProgress::getLastActivity))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProgressResponse upsert(Long userId, ProgressUpdateRequest request) {
        validateRelations(request.subjectId(), request.knowledgePointId());
        StudentProgress progress = find(userId, request.knowledgePointId());
        if (progress == null) {
            progress = new StudentProgress();
            progress.setUserId(userId);
            progress.setDeleted(0);
            progress.setVersion(0);
        }
        progress.setSubjectId(request.subjectId());
        progress.setKnowledgePointId(request.knowledgePointId());
        progress.setCurrentGrade(normalizeGrade(request.currentGrade()));
        progress.setAttemptCount(request.attemptCount() == null ? 0 : request.attemptCount());
        progress.setCorrectCount(request.correctCount() == null ? 0 : request.correctCount());
        progress.setMastered(Boolean.TRUE.equals(request.mastered()) ? 1 : 0);
        progress.setTransferPassed(Boolean.TRUE.equals(request.transferPassed()) ? 1 : 0);
        progress.setLastActivity(LocalDateTime.now());

        if (progress.getId() == null) {
            progressMapper.insert(progress);
        } else {
            progressMapper.updateById(progress);
        }
        return toResponse(progress);
    }

    @Override
    @Transactional
    public void applyRecord(
            Long userId,
            Long subjectId,
            Long knowledgePointId,
            BigDecimal score,
            Boolean mastered
    ) {
        if (subjectId == null || knowledgePointId == null) {
            return;
        }
        validateRelations(subjectId, knowledgePointId);
        StudentProgress progress = find(userId, knowledgePointId);
        if (progress == null) {
            progress = new StudentProgress();
            progress.setUserId(userId);
            progress.setSubjectId(subjectId);
            progress.setKnowledgePointId(knowledgePointId);
            progress.setCurrentGrade("C");
            progress.setAttemptCount(0);
            progress.setCorrectCount(0);
            progress.setMastered(0);
            progress.setTransferPassed(0);
            progress.setDeleted(0);
            progress.setVersion(0);
        }

        int attempts = progress.getAttemptCount() == null ? 0 : progress.getAttemptCount();
        int correct = progress.getCorrectCount() == null ? 0 : progress.getCorrectCount();
        boolean passed = Boolean.TRUE.equals(mastered) || (score != null && score.compareTo(BigDecimal.valueOf(60)) >= 0);

        progress.setAttemptCount(attempts + 1);
        progress.setCorrectCount(passed ? correct + 1 : correct);
        if (Boolean.TRUE.equals(mastered)) {
            progress.setMastered(1);
        }
        progress.setCurrentGrade(calculateGrade(score, Boolean.TRUE.equals(mastered)));
        progress.setLastActivity(LocalDateTime.now());

        if (progress.getId() == null) {
            progressMapper.insert(progress);
        } else {
            progressMapper.updateById(progress);
        }
    }

    private StudentProgress find(Long userId, Long knowledgePointId) {
        return progressMapper.selectOne(new LambdaQueryWrapper<StudentProgress>()
                .eq(StudentProgress::getUserId, userId)
                .eq(StudentProgress::getKnowledgePointId, knowledgePointId)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional
    public void applyGrade(
            Long userId,
            Long subjectId,
            Long knowledgePointId,
            BigDecimal score,
            Boolean mastered
    ) {
        if (subjectId == null || knowledgePointId == null) {
            return;
        }
        StudentProgress progress = find(userId, knowledgePointId);
        if (progress == null) {
            return;
        }
        if (Boolean.TRUE.equals(mastered)) {
            progress.setMastered(1);
        }
        progress.setCurrentGrade(calculateGrade(score, Boolean.TRUE.equals(mastered)));
        progress.setLastActivity(LocalDateTime.now());
        progressMapper.updateById(progress);
    }

    @Override
    @Transactional
    public void markTransferPassed(Long userId, Long subjectId, Long knowledgePointId) {
        if (subjectId == null || knowledgePointId == null) {
            return;
        }
        StudentProgress progress = find(userId, knowledgePointId);
        if (progress == null) {
            progress = new StudentProgress();
            progress.setUserId(userId);
            progress.setSubjectId(subjectId);
            progress.setKnowledgePointId(knowledgePointId);
            progress.setCurrentGrade("A");
            progress.setAttemptCount(0);
            progress.setCorrectCount(0);
            progress.setMastered(1);
            progress.setTransferPassed(1);
            progress.setDeleted(0);
            progress.setVersion(0);
            progress.setLastActivity(LocalDateTime.now());
            progressMapper.insert(progress);
            return;
        }
        progress.setTransferPassed(1);
        progress.setMastered(1);
        progress.setCurrentGrade("A");
        progress.setLastActivity(LocalDateTime.now());
        progressMapper.updateById(progress);
    }

    private ProgressResponse toResponse(StudentProgress progress) {
        Subject subject = subjectMapper.selectById(progress.getSubjectId());
        KnowledgePoint knowledgePoint = knowledgePointMapper.selectById(progress.getKnowledgePointId());
        return new ProgressResponse(
                progress.getId(),
                progress.getSubjectId(),
                subject == null ? null : subject.getName(),
                progress.getKnowledgePointId(),
                knowledgePoint == null ? null : knowledgePoint.getName(),
                progress.getCurrentGrade(),
                progress.getAttemptCount() == null ? 0 : progress.getAttemptCount(),
                progress.getCorrectCount() == null ? 0 : progress.getCorrectCount(),
                Integer.valueOf(1).equals(progress.getMastered()),
                Integer.valueOf(1).equals(progress.getTransferPassed()),
                progress.getLastActivity()
        );
    }

    private void validateRelations(Long subjectId, Long knowledgePointId) {
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学科不存在");
        }
        KnowledgePoint knowledgePoint = knowledgePointMapper.selectById(knowledgePointId);
        if (knowledgePoint == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识点不存在");
        }
        if (!subjectId.equals(knowledgePoint.getSubjectId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知识点不属于所选学科");
        }
    }

    private String calculateGrade(BigDecimal score, boolean mastered) {
        if (mastered || (score != null && score.compareTo(BigDecimal.valueOf(80)) >= 0)) {
            return "A";
        }
        if (score != null && score.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "B";
        }
        return "C";
    }

    private String normalizeGrade(String grade) {
        String normalized = grade == null ? "" : grade.trim().toUpperCase(Locale.ROOT);
        if (!List.of("A", "B", "C").contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "理解等级只能是 A、B 或 C");
        }
        return normalized;
    }
}
