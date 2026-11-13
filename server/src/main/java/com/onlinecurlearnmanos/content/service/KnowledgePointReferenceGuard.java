package com.onlinecurlearnmanos.content.service;

import com.onlinecurlearnmanos.chat.entity.ChatSession;
import com.onlinecurlearnmanos.chat.mapper.ChatSessionMapper;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassTask;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassTaskMapper;
import com.onlinecurlearnmanos.content.entity.Question;
import com.onlinecurlearnmanos.content.mapper.QuestionMapper;
import com.onlinecurlearnmanos.content.mapper.ResourceKnowledgeMapper;
import com.onlinecurlearnmanos.learning.entity.LearningPlanItem;
import com.onlinecurlearnmanos.learning.entity.LearningRecord;
import com.onlinecurlearnmanos.learning.entity.StudentProgress;
import com.onlinecurlearnmanos.learning.mapper.LearningPlanItemMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningRecordMapper;
import com.onlinecurlearnmanos.learning.mapper.StudentProgressMapper;
import com.onlinecurlearnmanos.practice.entity.PracticeAnswer;
import com.onlinecurlearnmanos.practice.entity.PracticeSession;
import com.onlinecurlearnmanos.practice.mapper.PracticeAnswerMapper;
import com.onlinecurlearnmanos.practice.mapper.PracticeSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识点引用检查。
 *
 * <p>知识点会被题目、学习资源、学习记录、学习进度、对话、练习与云班级任务引用，
 * 直接删除会撞外键或留下孤儿数据，因此删除前先统计引用，并把引用类型与条数交回调用方。</p>
 */
@Component
@RequiredArgsConstructor
public class KnowledgePointReferenceGuard {

    private final QuestionMapper questionMapper;
    private final ResourceKnowledgeMapper resourceKnowledgeMapper;
    private final LearningRecordMapper learningRecordMapper;
    private final StudentProgressMapper studentProgressMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final LearningPlanItemMapper learningPlanItemMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeAnswerMapper practiceAnswerMapper;
    private final CloudClassTaskMapper cloudClassTaskMapper;

    /**
     * 返回「引用类型 → 引用条数」，只保留大于 0 的项，顺序固定便于提示与测试断言。
     */
    public Map<String, Long> countReferences(Long knowledgePointId) {
        Map<String, Long> references = new LinkedHashMap<>();
        put(references, "题目", questionMapper.selectCount(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getKnowledgePointId, knowledgePointId)
        ));
        put(references, "学习资源", resourceKnowledgeMapper.countByKnowledgePointId(knowledgePointId));
        put(references, "学习记录", learningRecordMapper.selectCount(
                new LambdaQueryWrapper<LearningRecord>()
                        .eq(LearningRecord::getKnowledgePointId, knowledgePointId)
        ));
        put(references, "学习进度", studentProgressMapper.selectCount(
                new LambdaQueryWrapper<StudentProgress>()
                        .eq(StudentProgress::getKnowledgePointId, knowledgePointId)
        ));
        put(references, "对话记录", chatSessionMapper.selectCount(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getKnowledgePointId, knowledgePointId)
        ));
        put(references, "学习规划条目", learningPlanItemMapper.selectCount(
                new LambdaQueryWrapper<LearningPlanItem>()
                        .eq(LearningPlanItem::getKnowledgePointId, knowledgePointId)
        ));
        put(references, "练习会话", practiceSessionMapper.selectCount(
                new LambdaQueryWrapper<PracticeSession>()
                        .eq(PracticeSession::getKnowledgePointId, knowledgePointId)
        ));
        put(references, "练习作答", practiceAnswerMapper.selectCount(
                new LambdaQueryWrapper<PracticeAnswer>()
                        .eq(PracticeAnswer::getKnowledgePointId, knowledgePointId)
        ));
        put(references, "云班级任务", cloudClassTaskMapper.selectCount(
                new LambdaQueryWrapper<CloudClassTask>()
                        .eq(CloudClassTask::getKnowledgePointId, knowledgePointId)
        ));
        return references;
    }

    private void put(Map<String, Long> references, String label, Long count) {
        long value = count == null ? 0L : count;
        if (value > 0) {
            references.put(label, value);
        }
    }
}
