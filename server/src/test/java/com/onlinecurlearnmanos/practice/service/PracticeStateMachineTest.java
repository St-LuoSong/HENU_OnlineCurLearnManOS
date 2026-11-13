package com.onlinecurlearnmanos.practice.service;

import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.ai.service.LlmService;
import com.onlinecurlearnmanos.content.entity.Question;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.QuestionMapper;
import com.onlinecurlearnmanos.content.mapper.QuestionOptionMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.content.service.KnowledgeContextBuilder;
import com.onlinecurlearnmanos.learning.service.LearningRecordService;
import com.onlinecurlearnmanos.learning.service.ProgressService;
import com.onlinecurlearnmanos.practice.dto.PracticeMessageRequest;
import com.onlinecurlearnmanos.practice.entity.PracticeSession;
import com.onlinecurlearnmanos.practice.mapper.PracticeAnswerMapper;
import com.onlinecurlearnmanos.practice.mapper.PracticeMessageMapper;
import com.onlinecurlearnmanos.practice.mapper.PracticeSessionMapper;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 练习状态机：答题评级 → 迁移测试 → 已掌握，并保证「已掌握」与学习进度写回同步
 * （对应审计 B9：题库没有迁移题时不能只改本地状态）。
 */
@ExtendWith(MockitoExtension.class)
class PracticeStateMachineTest {

    private static final Long USER_ID = 31L;
    private static final Long SESSION_ID = 41L;
    private static final Long SUBJECT_ID = 5L;
    private static final Long POINT_ID = 77L;
    private static final Long QUESTION_ID = 88L;

    @Mock
    private PracticeSessionMapper sessionMapper;

    @Mock
    private PracticeMessageMapper messageMapper;

    @Mock
    private PracticeAnswerMapper answerMapper;

    @Mock
    private SubjectMapper subjectMapper;

    @Mock
    private KnowledgePointMapper knowledgePointMapper;

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private QuestionOptionMapper optionMapper;

    @Mock
    private KnowledgeContextBuilder knowledgeContextBuilder;

    @Mock
    private PracticePromptService promptService;

    @Mock
    private LlmService llmService;

    @Mock
    private LearningRecordService learningRecordService;

    @Mock
    private ProgressService progressService;

    private PracticeServiceImpl practiceService;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(PracticeSession.class);
    }

    @BeforeEach
    void setUp() {
        practiceService = new PracticeServiceImpl(
                sessionMapper,
                messageMapper,
                answerMapper,
                subjectMapper,
                knowledgePointMapper,
                questionMapper,
                optionMapper,
                knowledgeContextBuilder,
                promptService,
                llmService,
                learningRecordService,
                progressService,
                new ObjectMapper()
        );
    }

    @Test
    void marksMasteredAndWritesProgressWhenTransferQuestionIsMissing() {
        PracticeSession session = session("ASSESSING", 0);
        given(sessionMapper.selectOne(any())).willReturn(session);
        given(questionMapper.selectById(QUESTION_ID)).willReturn(question());
        given(optionMapper.selectList(any())).willReturn(List.of());
        given(promptService.evaluationPrompt(any(), any(), any())).willReturn("评估提示词");
        given(promptService.masteredMessage()).willReturn("你已经掌握了这个知识点。");
        stubEvaluation("""
                {"is_correct":true,"grade":"A","feedback":"思路清晰"}
                """);
        // 迁移题池为空：这是审计 B9 描述的场景
        given(questionMapper.selectList(any())).willReturn(List.of());

        practiceService.sendMessage(USER_ID, SESSION_ID, new PracticeMessageRequest("我的答案"));

        assertThat(session.getState()).isEqualTo("MASTERED");
        verify(progressService).markTransferPassed(USER_ID, SUBJECT_ID, POINT_ID);
        verify(learningRecordService).create(any(), any());
    }

    @Test
    void keepsAssessingWithoutWritingProgressWhenAnswerIsWrong() {
        PracticeSession session = session("ASSESSING", 0);
        given(sessionMapper.selectOne(any())).willReturn(session);
        given(questionMapper.selectById(QUESTION_ID)).willReturn(question());
        given(optionMapper.selectList(any())).willReturn(List.of());
        given(promptService.evaluationPrompt(any(), any(), any())).willReturn("评估提示词");
        stubEvaluation("""
                {"is_correct":false,"grade":"C","feedback":"再试一次"}
                """);

        practiceService.sendMessage(USER_ID, SESSION_ID, new PracticeMessageRequest("错误答案"));

        assertThat(session.getState()).isEqualTo("ASSESSING");
        verifyNoInteractions(progressService);
    }

    private void stubEvaluation(String payload) {
        given(llmService.execute(any(), any(), any(), any(), any()))
                .willAnswer(invocation -> invokeBusinessAction(invocation.getArgument(4), payload));
    }

    @SuppressWarnings("unchecked")
    private Object invokeBusinessAction(Object businessAction, String payload) {
        Function<LlmChatResponse, Object> action =
                (Function<LlmChatResponse, Object>) businessAction;
        return action.apply(new LlmChatResponse(
                payload,
                "gpt-4o-mini",
                "openai",
                null,
                "req-1"
        ));
    }

    private PracticeSession session(String state, int currentTransfer) {
        PracticeSession session = new PracticeSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setSubjectId(SUBJECT_ID);
        session.setKnowledgePointId(POINT_ID);
        session.setTitle("导数练习");
        session.setState(state);
        session.setCurrentQuestionId(QUESTION_ID);
        session.setCurrentTransfer(currentTransfer);
        session.setConsecutiveFailures(0);
        session.setDeleted(0);
        session.setVersion(0);
        return session;
    }

    private Question question() {
        Question question = new Question();
        question.setId(QUESTION_ID);
        question.setSubjectId(SUBJECT_ID);
        question.setKnowledgePointId(POINT_ID);
        question.setQuestionType("SINGLE_CHOICE");
        question.setContent("导数的几何意义是什么？");
        question.setCorrectAnswer("切线斜率");
        question.setIsTransfer(0);
        return question;
    }
}
