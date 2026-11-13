package com.onlinecurlearnmanos.content.service;

import com.onlinecurlearnmanos.content.dto.SubjectResponse;
import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.entity.Question;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.QuestionMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private final SubjectMapper subjectMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final QuestionMapper questionMapper;

    @Override
    public List<SubjectResponse> listSubjects() {
        return subjectMapper.selectList(new LambdaQueryWrapper<Subject>()
                        .orderByAsc(Subject::getSortOrder)
                        .orderByAsc(Subject::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private SubjectResponse toResponse(Subject subject) {
        long knowledgePointCount = knowledgePointMapper.selectCount(
                new LambdaQueryWrapper<KnowledgePoint>()
                        .eq(KnowledgePoint::getSubjectId, subject.getId())
        );
        long questionCount = questionMapper.selectCount(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getSubjectId, subject.getId())
        );
        return new SubjectResponse(
                subject.getId(),
                subject.getCode(),
                subject.getName(),
                subject.getIcon(),
                subject.getColor(),
                knowledgePointCount,
                questionCount
        );
    }
}
