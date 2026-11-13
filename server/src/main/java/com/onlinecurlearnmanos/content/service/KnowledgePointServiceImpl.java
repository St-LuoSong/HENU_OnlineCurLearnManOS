package com.onlinecurlearnmanos.content.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.dto.KnowledgePointResponse;
import com.onlinecurlearnmanos.content.dto.KnowledgePointSaveRequest;
import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.entity.KnowledgePointDetail;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointDetailMapper;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgePointServiceImpl implements KnowledgePointService {

    private static final String KEY_POINT = "KEY_POINT";
    private static final String COMMON_MISTAKE = "COMMON_MISTAKE";
    private static final String INTUITION_PUMP = "INTUITION_PUMP";

    private final KnowledgePointMapper knowledgePointMapper;
    private final KnowledgePointDetailMapper detailMapper;
    private final SubjectMapper subjectMapper;
    private final KnowledgePointReferenceGuard referenceGuard;

    @Override
    public List<KnowledgePointResponse> list(Long subjectId, String keyword) {
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<>();
        if (subjectId != null) {
            wrapper.eq(KnowledgePoint::getSubjectId, subjectId);
        }
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(condition -> condition
                    .like(KnowledgePoint::getName, normalizedKeyword)
                    .or()
                    .like(KnowledgePoint::getTitle, normalizedKeyword)
                    .or()
                    .like(KnowledgePoint::getContent, normalizedKeyword)
            );
        }
        wrapper.orderByAsc(KnowledgePoint::getSubjectId).orderByAsc(KnowledgePoint::getId);
        return knowledgePointMapper.selectList(wrapper)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public KnowledgePointResponse get(Long id) {
        return toResponse(getRequired(id));
    }

    @Override
    @Transactional
    public KnowledgePointResponse create(KnowledgePointSaveRequest request) {
        Subject subject = getRequiredSubject(request.subjectId());
        KnowledgePoint knowledgePoint = new KnowledgePoint();
        applyRequest(knowledgePoint, request);
        knowledgePoint.setDeleted(0);
        knowledgePoint.setVersion(0);
        knowledgePointMapper.insert(knowledgePoint);
        saveDetails(knowledgePoint.getId(), request);
        return toResponse(knowledgePoint, subject);
    }

    @Override
    @Transactional
    public KnowledgePointResponse update(Long id, KnowledgePointSaveRequest request) {
        KnowledgePoint knowledgePoint = getRequired(id);
        Subject subject = getRequiredSubject(request.subjectId());
        knowledgePointMapper.update(null, new LambdaUpdateWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getId, id)
                .set(KnowledgePoint::getSubjectId, request.subjectId())
                .set(KnowledgePoint::getCode, request.code().trim())
                .set(KnowledgePoint::getName, request.name().trim())
                .set(KnowledgePoint::getTitle, request.title().trim())
                .set(KnowledgePoint::getContent, request.content())
                .set(
                        KnowledgePoint::getSourceType,
                        StringUtils.hasText(request.sourceType()) ? request.sourceType().trim() : "TEXT"
                )
                .set(KnowledgePoint::getSourceUrl, normalizeNullable(request.sourceUrl())));
        detailMapper.delete(new LambdaQueryWrapper<KnowledgePointDetail>()
                .eq(KnowledgePointDetail::getKnowledgePointId, id));
        saveDetails(id, request);
        return toResponse(getRequired(id), subject);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getRequired(id);
        Map<String, Long> references = referenceGuard.countReferences(id);
        if (!references.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, describeReferences(references));
        }
        detailMapper.delete(new LambdaQueryWrapper<KnowledgePointDetail>()
                .eq(KnowledgePointDetail::getKnowledgePointId, id));
        knowledgePointMapper.deleteById(id);
    }

    private String describeReferences(Map<String, Long> references) {
        String detail = references.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue() + " 条")
                .collect(Collectors.joining("、"));
        return "该知识点仍被引用，无法删除：" + detail + "。请先解除关联。";
    }

    private void applyRequest(KnowledgePoint knowledgePoint, KnowledgePointSaveRequest request) {
        knowledgePoint.setSubjectId(request.subjectId());
        knowledgePoint.setCode(request.code().trim());
        knowledgePoint.setName(request.name().trim());
        knowledgePoint.setTitle(request.title().trim());
        knowledgePoint.setContent(request.content());
        knowledgePoint.setSourceType(
                StringUtils.hasText(request.sourceType()) ? request.sourceType().trim() : "TEXT"
        );
        knowledgePoint.setSourceUrl(normalizeNullable(request.sourceUrl()));
    }

    private void saveDetails(Long knowledgePointId, KnowledgePointSaveRequest request) {
        saveDetailList(knowledgePointId, KEY_POINT, request.keyPoints());
        saveDetailList(knowledgePointId, COMMON_MISTAKE, request.commonMistakes());
        saveDetailList(knowledgePointId, INTUITION_PUMP, request.intuitionPumps());
    }

    private void saveDetailList(Long knowledgePointId, String detailType, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        int sortOrder = 1;
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            KnowledgePointDetail detail = new KnowledgePointDetail();
            detail.setKnowledgePointId(knowledgePointId);
            detail.setDetailType(detailType);
            detail.setContent(value.trim());
            detail.setSortOrder(sortOrder++);
            detailMapper.insert(detail);
        }
    }

    private KnowledgePoint getRequired(Long id) {
        KnowledgePoint knowledgePoint = knowledgePointMapper.selectById(id);
        if (knowledgePoint == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识点不存在");
        }
        return knowledgePoint;
    }

    private Subject getRequiredSubject(Long subjectId) {
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学科不存在");
        }
        return subject;
    }

    private KnowledgePointResponse toResponse(KnowledgePoint knowledgePoint) {
        return toResponse(knowledgePoint, getRequiredSubject(knowledgePoint.getSubjectId()));
    }

    private KnowledgePointResponse toResponse(KnowledgePoint knowledgePoint, Subject subject) {
        List<KnowledgePointDetail> details = detailMapper.selectList(
                new LambdaQueryWrapper<KnowledgePointDetail>()
                        .eq(KnowledgePointDetail::getKnowledgePointId, knowledgePoint.getId())
                        .orderByAsc(KnowledgePointDetail::getDetailType)
                        .orderByAsc(KnowledgePointDetail::getSortOrder)
        );
        return new KnowledgePointResponse(
                knowledgePoint.getId(),
                knowledgePoint.getSubjectId(),
                subject.getCode(),
                subject.getName(),
                knowledgePoint.getCode(),
                knowledgePoint.getName(),
                knowledgePoint.getTitle(),
                knowledgePoint.getContent(),
                knowledgePoint.getSourceType(),
                knowledgePoint.getSourceUrl(),
                contentsOf(details, KEY_POINT),
                contentsOf(details, COMMON_MISTAKE),
                contentsOf(details, INTUITION_PUMP)
        );
    }

    private List<String> contentsOf(List<KnowledgePointDetail> details, String detailType) {
        return details.stream()
                .filter(detail -> Objects.equals(detailType, detail.getDetailType()))
                .map(KnowledgePointDetail::getContent)
                .toList();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
