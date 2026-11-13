package com.onlinecurlearnmanos.content.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.dto.QuestionOptionResponse;
import com.onlinecurlearnmanos.content.dto.QuestionResponse;
import com.onlinecurlearnmanos.content.dto.QuestionSaveRequest;
import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.entity.Question;
import com.onlinecurlearnmanos.content.entity.QuestionOption;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.QuestionMapper;
import com.onlinecurlearnmanos.content.mapper.QuestionOptionMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final SubjectMapper subjectMapper;
    private final KnowledgePointMapper knowledgePointMapper;

    @Override
    public List<QuestionResponse> list(Long subjectId, Long knowledgePointId, String questionType) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        if (subjectId != null) {
            wrapper.eq(Question::getSubjectId, subjectId);
        }
        if (knowledgePointId != null) {
            wrapper.eq(Question::getKnowledgePointId, knowledgePointId);
        }
        if (StringUtils.hasText(questionType)) {
            wrapper.eq(Question::getQuestionType, questionType.trim().toUpperCase());
        }
        wrapper.orderByAsc(Question::getSubjectId).orderByAsc(Question::getId);
        return questionMapper.selectList(wrapper)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public QuestionResponse get(Long id) {
        return toResponse(getRequired(id));
    }

    @Override
    @Transactional
    public QuestionResponse create(QuestionSaveRequest request) {
        validateRelations(request.subjectId(), request.knowledgePointId());
        Question question = new Question();
        applyRequest(question, request);
        question.setDeleted(0);
        question.setVersion(0);
        questionMapper.insert(question);
        saveOptions(question.getId(), request);
        return toResponse(question);
    }

    @Override
    @Transactional
    public QuestionResponse update(Long id, QuestionSaveRequest request) {
        Question question = getRequired(id);
        validateRelations(request.subjectId(), request.knowledgePointId());
        questionMapper.update(null, new LambdaUpdateWrapper<Question>()
                .eq(Question::getId, id)
                .set(Question::getSubjectId, request.subjectId())
                .set(Question::getKnowledgePointId, request.knowledgePointId())
                .set(Question::getQuestionType, request.questionType().trim().toUpperCase())
                .set(Question::getDifficulty, request.difficulty())
                .set(Question::getContent, request.content())
                .set(Question::getCorrectAnswer, request.correctAnswer())
                .set(Question::getExplanation, normalizeNullable(request.explanation()))
                .set(Question::getIsTransfer, Boolean.TRUE.equals(request.transfer()) ? 1 : 0));
        optionMapper.delete(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, id));
        saveOptions(id, request);
        return toResponse(getRequired(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getRequired(id);
        optionMapper.delete(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, id));
        questionMapper.deleteById(id);
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
        if (!subject.getId().equals(knowledgePoint.getSubjectId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知识点不属于所选学科");
        }
    }

    private void applyRequest(Question question, QuestionSaveRequest request) {
        question.setSubjectId(request.subjectId());
        question.setKnowledgePointId(request.knowledgePointId());
        question.setQuestionType(request.questionType().trim().toUpperCase());
        question.setDifficulty(request.difficulty());
        question.setContent(request.content());
        question.setCorrectAnswer(request.correctAnswer());
        question.setExplanation(normalizeNullable(request.explanation()));
        question.setIsTransfer(Boolean.TRUE.equals(request.transfer()) ? 1 : 0);
    }

    private void saveOptions(Long questionId, QuestionSaveRequest request) {
        if (request.options() == null || request.options().isEmpty()) {
            return;
        }
        Set<String> optionKeys = new HashSet<>();
        int sortOrder = 1;
        for (var optionRequest : request.options()) {
            String optionKey = optionRequest.key().trim().toUpperCase();
            if (!optionKeys.add(optionKey)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "选项标识不能重复");
            }
            QuestionOption option = new QuestionOption();
            option.setQuestionId(questionId);
            option.setOptionKey(optionKey);
            option.setOptionText(optionRequest.text().trim());
            option.setSortOrder(sortOrder++);
            optionMapper.insert(option);
        }
    }

    private Question getRequired(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        return question;
    }

    private QuestionResponse toResponse(Question question) {
        Subject subject = subjectMapper.selectById(question.getSubjectId());
        KnowledgePoint knowledgePoint = knowledgePointMapper.selectById(question.getKnowledgePointId());
        List<QuestionOptionResponse> options = optionMapper.selectList(
                        new LambdaQueryWrapper<QuestionOption>()
                                .eq(QuestionOption::getQuestionId, question.getId())
                                .orderByAsc(QuestionOption::getSortOrder)
                )
                .stream()
                .map(option -> new QuestionOptionResponse(option.getOptionKey(), option.getOptionText()))
                .toList();
        return new QuestionResponse(
                question.getId(),
                question.getSubjectId(),
                subject == null ? null : subject.getName(),
                question.getKnowledgePointId(),
                knowledgePoint == null ? null : knowledgePoint.getName(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.getContent(),
                question.getCorrectAnswer(),
                question.getExplanation(),
                Integer.valueOf(1).equals(question.getIsTransfer()),
                options
        );
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
