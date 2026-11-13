package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.entity.LearningResource;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.LearningResourceMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.learning.dto.LearningRecordResponse;
import com.onlinecurlearnmanos.learning.dto.LearningRecordSaveRequest;
import com.onlinecurlearnmanos.learning.entity.LearningRecord;
import com.onlinecurlearnmanos.learning.mapper.LearningRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl implements LearningRecordService {

    private final LearningRecordMapper recordMapper;
    private final SubjectMapper subjectMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final LearningResourceMapper resourceMapper;
    private final ProgressService progressService;

    @Override
    public List<LearningRecordResponse> list(Long userId, LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<LearningRecord> wrapper = new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId);
        if (from != null) {
            wrapper.ge(LearningRecord::getRecordedAt, from);
        }
        if (to != null) {
            wrapper.le(LearningRecord::getRecordedAt, to);
        }
        wrapper.orderByDesc(LearningRecord::getRecordedAt).orderByDesc(LearningRecord::getId);
        return recordMapper.selectList(wrapper)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public LearningRecordResponse create(Long userId, LearningRecordSaveRequest request) {
        validateRelations(request.subjectId(), request.knowledgePointId(), request.resourceId());

        LearningRecord record = new LearningRecord();
        record.setUserId(userId);
        record.setSubjectId(request.subjectId());
        record.setKnowledgePointId(request.knowledgePointId());
        record.setResourceId(request.resourceId());
        record.setRecordType(request.recordType().trim().toUpperCase());
        record.setDurationMinutes(request.durationMinutes() == null ? 0 : request.durationMinutes());
        record.setScore(request.score());
        record.setMastered(Boolean.TRUE.equals(request.mastered()) ? 1 : 0);
        record.setRemark(normalizeNullable(request.remark()));
        record.setRecordedAt(request.recordedAt() == null ? LocalDateTime.now() : request.recordedAt());
        record.setDeleted(0);
        record.setVersion(0);
        recordMapper.insert(record);

        progressService.applyRecord(
                userId,
                request.subjectId(),
                request.knowledgePointId(),
                request.score(),
                request.mastered()
        );
        return toResponse(record);
    }

    @Override
    @Transactional
    public void updateScore(Long recordId, BigDecimal score, Boolean mastered, String remark) {
        LearningRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            return;
        }
        recordMapper.update(null, new LambdaUpdateWrapper<LearningRecord>()
                .eq(LearningRecord::getId, recordId)
                .set(LearningRecord::getScore, score)
                .set(LearningRecord::getMastered, Boolean.TRUE.equals(mastered) ? 1 : 0)
                .set(LearningRecord::getRemark, normalizeNullable(remark)));
        progressService.applyGrade(
                record.getUserId(),
                record.getSubjectId(),
                record.getKnowledgePointId(),
                score,
                mastered
        );
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        LearningRecord record = recordMapper.selectOne(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getId, id)
                .eq(LearningRecord::getUserId, userId)
                .last("LIMIT 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学习记录不存在");
        }
        recordMapper.deleteById(id);
    }

    private void validateRelations(Long subjectId, Long knowledgePointId, Long resourceId) {
        if (subjectId != null && subjectMapper.selectById(subjectId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学科不存在");
        }
        if (knowledgePointId != null) {
            KnowledgePoint knowledgePoint = knowledgePointMapper.selectById(knowledgePointId);
            if (knowledgePoint == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "知识点不存在");
            }
            if (subjectId != null && !subjectId.equals(knowledgePoint.getSubjectId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "知识点不属于所选学科");
            }
        }
        if (resourceId != null) {
            LearningResource resource = resourceMapper.selectById(resourceId);
            if (resource == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "学习资源不存在");
            }
            if (subjectId != null && !subjectId.equals(resource.getSubjectId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "学习资源不属于所选学科");
            }
        }
    }

    private LearningRecordResponse toResponse(LearningRecord record) {
        Subject subject = record.getSubjectId() == null ? null : subjectMapper.selectById(record.getSubjectId());
        KnowledgePoint knowledgePoint = record.getKnowledgePointId() == null
                ? null
                : knowledgePointMapper.selectById(record.getKnowledgePointId());
        LearningResource resource = record.getResourceId() == null
                ? null
                : resourceMapper.selectById(record.getResourceId());
        return new LearningRecordResponse(
                record.getId(),
                subject == null ? null : subject.getName(),
                knowledgePoint == null ? null : knowledgePoint.getName(),
                resource == null ? null : resource.getTitle(),
                record.getRecordType(),
                record.getDurationMinutes(),
                record.getScore(),
                Integer.valueOf(1).equals(record.getMastered()),
                record.getRemark(),
                record.getRecordedAt()
        );
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
