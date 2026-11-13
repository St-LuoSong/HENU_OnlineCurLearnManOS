package com.onlinecurlearnmanos.content.service;

import com.onlinecurlearnmanos.chat.mapper.ChatSessionMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassTaskMapper;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointDetailMapper;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.QuestionMapper;
import com.onlinecurlearnmanos.content.mapper.ResourceKnowledgeMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningPlanItemMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningRecordMapper;
import com.onlinecurlearnmanos.learning.mapper.StudentProgressMapper;
import com.onlinecurlearnmanos.practice.mapper.PracticeAnswerMapper;
import com.onlinecurlearnmanos.practice.mapper.PracticeSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 知识点删除前的引用检查：仍被题目、资源、记录、进度、对话、练习或云班级任务引用时必须拒绝，
 * 并给出可定位的引用类型与条数；没有任何引用时才真正删除。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgePointDeleteGuardTest {

    private static final Long POINT_ID = 77L;

    @Mock
    private KnowledgePointMapper knowledgePointMapper;

    @Mock
    private KnowledgePointDetailMapper detailMapper;

    @Mock
    private SubjectMapper subjectMapper;

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private ResourceKnowledgeMapper resourceKnowledgeMapper;

    @Mock
    private LearningRecordMapper learningRecordMapper;

    @Mock
    private StudentProgressMapper studentProgressMapper;

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Mock
    private LearningPlanItemMapper learningPlanItemMapper;

    @Mock
    private PracticeSessionMapper practiceSessionMapper;

    @Mock
    private PracticeAnswerMapper practiceAnswerMapper;

    @Mock
    private CloudClassTaskMapper cloudClassTaskMapper;

    private KnowledgePointServiceImpl knowledgePointService;

    @BeforeEach
    void setUp() {
        KnowledgePointReferenceGuard guard = new KnowledgePointReferenceGuard(
                questionMapper,
                resourceKnowledgeMapper,
                learningRecordMapper,
                studentProgressMapper,
                chatSessionMapper,
                learningPlanItemMapper,
                practiceSessionMapper,
                practiceAnswerMapper,
                cloudClassTaskMapper
        );
        knowledgePointService = new KnowledgePointServiceImpl(
                knowledgePointMapper,
                detailMapper,
                subjectMapper,
                guard
        );
    }

    @Test
    void rejectsDeleteWhenKnowledgePointIsStillReferenced() {
        given(knowledgePointMapper.selectById(POINT_ID)).willReturn(knowledgePoint());
        stubReferences(3L, 2L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

        assertThatThrownBy(() -> knowledgePointService.delete(POINT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("题目 3 条")
                .hasMessageContaining("学习资源 2 条");

        verify(detailMapper, never()).delete(any());
        verify(knowledgePointMapper, never()).deleteById(anyLong());
    }

    @Test
    void deletesPointAndDetailsWhenNothingReferencesIt() {
        given(knowledgePointMapper.selectById(POINT_ID)).willReturn(knowledgePoint());
        stubReferences(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

        knowledgePointService.delete(POINT_ID);

        verify(detailMapper).delete(any());
        verify(knowledgePointMapper).deleteById(POINT_ID);
    }

    private void stubReferences(
            long question,
            long resource,
            long record,
            long progress,
            long chat,
            long planItem,
            long practiceSession,
            long practiceAnswer,
            long cloudTask
    ) {
        given(questionMapper.selectCount(any())).willReturn(question);
        given(resourceKnowledgeMapper.countByKnowledgePointId(POINT_ID)).willReturn(resource);
        given(learningRecordMapper.selectCount(any())).willReturn(record);
        given(studentProgressMapper.selectCount(any())).willReturn(progress);
        given(chatSessionMapper.selectCount(any())).willReturn(chat);
        given(learningPlanItemMapper.selectCount(any())).willReturn(planItem);
        given(practiceSessionMapper.selectCount(any())).willReturn(practiceSession);
        given(practiceAnswerMapper.selectCount(any())).willReturn(practiceAnswer);
        given(cloudClassTaskMapper.selectCount(any())).willReturn(cloudTask);
    }

    private KnowledgePoint knowledgePoint() {
        KnowledgePoint knowledgePoint = new KnowledgePoint();
        knowledgePoint.setId(POINT_ID);
        knowledgePoint.setSubjectId(5L);
        knowledgePoint.setCode("KP-001");
        knowledgePoint.setName("导数的定义");
        knowledgePoint.setTitle("导数：从平均变化率到瞬时变化率");
        return knowledgePoint;
    }
}
