package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.entity.LearningResource;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.LearningResourceMapper;
import com.onlinecurlearnmanos.learning.dto.GeneratedLearningPlan;
import com.onlinecurlearnmanos.learning.dto.GeneratedLearningPlanItem;
import com.onlinecurlearnmanos.learning.entity.LearningPlan;
import com.onlinecurlearnmanos.learning.entity.LearningPlanItem;
import com.onlinecurlearnmanos.learning.mapper.LearningPlanItemMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningPlanMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 学习规划落库前的引用校验（审计 B8）：模型幻觉出的资源 / 知识点 ID 置空而不是让整份计划失败，
 * 学习任务必须属于当前用户，否则一样置空。
 */
@ExtendWith(MockitoExtension.class)
class LearningPlanPersistenceTest {

    private static final Long USER_ID = 31L;

    @Mock
    private LearningPlanMapper planMapper;

    @Mock
    private LearningPlanItemMapper itemMapper;

    @Mock
    private LearningResourceMapper resourceMapper;

    @Mock
    private KnowledgePointMapper knowledgePointMapper;

    @Mock
    private LearningTaskMapper taskMapper;

    @InjectMocks
    private LearningPlanPersistenceService persistenceService;

    @Test
    void dropsReferencesThatDoNotExistOrBelongToOthers() {
        stubPlanInsert(7L);
        given(resourceMapper.selectById(1L)).willReturn(null);
        given(knowledgePointMapper.selectById(2L)).willReturn(null);
        given(taskMapper.selectCount(any())).willReturn(0L);

        persistenceService.save(USER_ID, generatedPlan(1L, 2L, 3L), providerResponse());

        LearningPlanItem saved = captureInsertedItem();
        assertThat(saved.getResourceId()).isNull();
        assertThat(saved.getKnowledgePointId()).isNull();
        assertThat(saved.getTaskId()).isNull();
        assertThat(saved.getStatus()).isEqualTo("TODO");
        assertThat(saved.getSortOrder()).isEqualTo(1);
        assertThat(saved.getPlanId()).isEqualTo(7L);
    }

    @Test
    void keepsReferencesThatExistAndBelongToCurrentUser() {
        stubPlanInsert(7L);
        given(resourceMapper.selectById(1L)).willReturn(new LearningResource());
        given(knowledgePointMapper.selectById(2L)).willReturn(new KnowledgePoint());
        given(taskMapper.selectCount(any())).willReturn(1L);

        persistenceService.save(USER_ID, generatedPlan(1L, 2L, 3L), providerResponse());

        LearningPlanItem saved = captureInsertedItem();
        assertThat(saved.getResourceId()).isEqualTo(1L);
        assertThat(saved.getKnowledgePointId()).isEqualTo(2L);
        assertThat(saved.getTaskId()).isEqualTo(3L);
    }

    @Test
    void rejectsIncompletePlanWithoutWritingAnything() {
        assertThatThrownBy(() -> persistenceService.save(
                USER_ID,
                new GeneratedLearningPlan("   ", null, null),
                providerResponse()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不完整");

        verifyNoInteractions(planMapper, itemMapper);
    }

    private void stubPlanInsert(Long planId) {
        given(planMapper.insert(any(LearningPlan.class))).willAnswer(invocation -> {
            LearningPlan plan = invocation.getArgument(0);
            plan.setId(planId);
            return 1;
        });
    }

    private LearningPlanItem captureInsertedItem() {
        ArgumentCaptor<LearningPlanItem> captor = ArgumentCaptor.forClass(LearningPlanItem.class);
        verify(itemMapper).insert(captor.capture());
        return captor.getValue();
    }

    private GeneratedLearningPlan generatedPlan(Long resourceId, Long pointId, Long taskId) {
        return new GeneratedLearningPlan(
                "考研数学复习计划",
                "每天两小时",
                List.of(new GeneratedLearningPlanItem(
                        "高数第一章",
                        "完成极限与连续",
                        null,
                        null,
                        resourceId,
                        pointId,
                        taskId
                ))
        );
    }

    private LlmChatResponse providerResponse() {
        return new LlmChatResponse("{}", "gpt-4o-mini", "openai", null, "req-1");
    }
}
