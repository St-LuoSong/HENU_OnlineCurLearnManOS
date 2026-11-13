package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.ai.service.LlmService;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.learning.dto.LearningPlanGenerateRequest;
import com.onlinecurlearnmanos.learning.dto.LearningPlanResponse;
import com.onlinecurlearnmanos.learning.mapper.LearningPlanItemMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningPlanMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 学习规划的计费与失败语义：AI 生成必须走 {@code LlmService.execute} 这个唯一计费入口，
 * 模型输出不可解析或业务落库失败时必须把异常抛回计费层，才能触发预占积分退款。
 */
@ExtendWith(MockitoExtension.class)
class LearningPlanBillingTest {

    private static final Long USER_ID = 31L;

    @Mock
    private LearningPlanMapper planMapper;

    @Mock
    private LearningPlanItemMapper itemMapper;

    @Mock
    private LearningPlanPersistenceService persistenceService;

    @Mock
    private LlmService llmService;

    @Mock
    private GoalService goalService;

    @Mock
    private TaskService taskService;

    private LearningPlanServiceImpl learningPlanService;

    @BeforeEach
    void setUp() {
        learningPlanService = new LearningPlanServiceImpl(
                planMapper,
                itemMapper,
                persistenceService,
                llmService,
                JsonMapper.builder().addModule(new JavaTimeModule()).build(),
                goalService,
                taskService
        );
    }

    @Test
    void generateGoesThroughBillingEntry() {
        LearningPlanResponse canned = new LearningPlanResponse(
                7L,
                null,
                "考研数学复习计划",
                "每天两小时，先过基础再做真题",
                "DRAFT",
                "openai",
                "gpt-4o-mini",
                null,
                null,
                3,
                0,
                0,
                List.of()
        );
        given(llmService.execute(any(), any(), any(), any(), any())).willReturn(canned);

        LearningPlanResponse response = learningPlanService.generate(
                USER_ID,
                new LearningPlanGenerateRequest("准备考研数学")
        );

        assertThat(response).isSameAs(canned);
        verify(llmService).execute(
                eq(USER_ID),
                any(),
                eq("AI_PLAN"),
                eq("AI 学习规划生成"),
                any()
        );
        verifyNoInteractions(persistenceService);
    }

    @Test
    void unparsableModelOutputFailsBusinessActionWithoutPersisting() {
        stubModelResponse("这不是 JSON");

        assertThatThrownBy(() -> learningPlanService.generate(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 生成学习计划失败");

        verifyNoInteractions(persistenceService);
    }

    @Test
    void persistenceFailurePropagatesUnchangedSoReservedPointsCanBeRefunded() {
        stubModelResponse(validPlanJson());
        given(persistenceService.save(any(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.BAD_REQUEST, "AI 返回的学习计划不完整"));

        assertThatThrownBy(() -> learningPlanService.generate(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST))
                .hasMessageContaining("学习计划不完整");
    }

    private LearningPlanGenerateRequest request() {
        return new LearningPlanGenerateRequest("准备考研数学");
    }

    private void stubModelResponse(String content) {
        given(llmService.execute(any(), any(), any(), any(), any())).willAnswer(invocation -> {
            Function<LlmChatResponse, Object> action = invocation.getArgument(4);
            return action.apply(new LlmChatResponse(
                    content,
                    "gpt-4o-mini",
                    "openai",
                    null,
                    "req-1"
            ));
        });
    }

    private String validPlanJson() {
        return """
                {"title":"考研数学复习计划","summary":"每天两小时","items":[
                  {"title":"高数第一章","description":"完成极限与连续","startDate":"2026-09-21",
                   "endDate":"2026-09-27","resourceId":null,"knowledgePointId":null,"taskId":null}
                ]}
                """;
    }
}
