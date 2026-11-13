package com.onlinecurlearnmanos.content.service;

import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.entity.KnowledgePointDetail;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointDetailMapper;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 把知识点及其明细整理成结构化上下文，供 AI 提示词引用。
 * 这样业务代码不需要直接拼装知识点表结构，也避免提示词无节制膨胀。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeContextBuilder {

    private static final int MAX_POINTS = 6;
    private static final int MAX_DETAILS = 4;
    private static final String KEY_POINT = "KEY_POINT";
    private static final String COMMON_MISTAKE = "COMMON_MISTAKE";
    private static final String INTUITION_PUMP = "INTUITION_PUMP";

    private final KnowledgePointMapper knowledgePointMapper;
    private final KnowledgePointDetailMapper detailMapper;

    public String build(Long subjectId, Long knowledgePointId) {
        List<KnowledgePoint> points = resolvePoints(subjectId, knowledgePointId);
        if (points.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (KnowledgePoint point : points) {
            builder.append("### ").append(point.getName()).append('\n');
            if (StringUtils.hasText(point.getContent())) {
                builder.append(point.getContent().trim()).append('\n');
            }
            List<KnowledgePointDetail> details = detailMapper.selectList(
                    new LambdaQueryWrapper<KnowledgePointDetail>()
                            .eq(KnowledgePointDetail::getKnowledgePointId, point.getId())
                            .orderByAsc(KnowledgePointDetail::getSortOrder)
                            .orderByAsc(KnowledgePointDetail::getId)
            );
            appendGroup(builder, details, KEY_POINT, "关键要点");
            appendGroup(builder, details, COMMON_MISTAKE, "常见误区");
            appendGroup(builder, details, INTUITION_PUMP, "直觉提示");
        }
        return builder.toString();
    }

    private List<KnowledgePoint> resolvePoints(Long subjectId, Long knowledgePointId) {
        if (knowledgePointId != null) {
            KnowledgePoint point = knowledgePointMapper.selectById(knowledgePointId);
            return point == null ? List.of() : List.of(point);
        }
        if (subjectId == null) {
            return List.of();
        }
        return knowledgePointMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getSubjectId, subjectId)
                .orderByAsc(KnowledgePoint::getId)
                .last("LIMIT " + MAX_POINTS));
    }

    private void appendGroup(
            StringBuilder builder,
            List<KnowledgePointDetail> details,
            String detailType,
            String label
    ) {
        List<String> contents = details.stream()
                .filter(detail -> detailType.equalsIgnoreCase(detail.getDetailType()))
                .map(KnowledgePointDetail::getContent)
                .filter(StringUtils::hasText)
                .limit(MAX_DETAILS)
                .toList();
        if (contents.isEmpty()) {
            return;
        }
        builder.append("- ").append(label).append("：")
                .append(String.join("；", contents))
                .append('\n');
    }
}
