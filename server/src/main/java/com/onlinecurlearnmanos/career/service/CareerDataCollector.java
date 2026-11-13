package com.onlinecurlearnmanos.career.service;

import com.onlinecurlearnmanos.career.dto.CareerDataPreviewResponse;
import com.onlinecurlearnmanos.career.entity.CareerProfile;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassSubmission;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassSubmissionMapper;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.learning.entity.LearningGoal;
import com.onlinecurlearnmanos.learning.entity.LearningRecord;
import com.onlinecurlearnmanos.learning.entity.LearningTask;
import com.onlinecurlearnmanos.learning.entity.StudentProgress;
import com.onlinecurlearnmanos.learning.mapper.LearningGoalMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningRecordMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningTaskMapper;
import com.onlinecurlearnmanos.learning.mapper.StudentProgressMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CareerDataCollector {

    private static final int ENABLED = 1;
    private static final int SCAN_LIMIT = 200;
    private static final int MAX_SUBJECTS = 8;
    private static final int MAX_GOAL_TITLES = 6;

    private final LearningRecordMapper recordMapper;
    private final LearningGoalMapper goalMapper;
    private final LearningTaskMapper taskMapper;
    private final StudentProgressMapper progressMapper;
    private final SubjectMapper subjectMapper;
    private final CloudClassSubmissionMapper submissionMapper;

    public CareerDataPreviewResponse collect(CareerProfile profile) {
        Long userId = profile.getUserId();
        boolean withRecords = isOn(profile.getIncludeLearningRecords());
        boolean withGoals = isOn(profile.getIncludeGoals());
        boolean withGrades = isOn(profile.getIncludeGrades());

        int learningRecordCount = 0;
        int learningMinutes = 0;
        int masteredPointCount = 0;
        int goalCount = 0;
        int taskCount = 0;
        int completedTaskCount = 0;
        int gradedSubmissionCount = 0;
        List<String> subjects = List.of();
        List<String> goalTitles = List.of();
        List<Double> scores = new ArrayList<>();

        if (withRecords) {
            List<LearningRecord> records = recordMapper.selectList(
                    new LambdaQueryWrapper<LearningRecord>()
                            .eq(LearningRecord::getUserId, userId)
                            .orderByDesc(LearningRecord::getRecordedAt)
                            .last("LIMIT " + SCAN_LIMIT)
            );
            learningRecordCount = records.size();
            learningMinutes = records.stream()
                    .mapToInt(record -> record.getDurationMinutes() == null
                            ? 0
                            : Math.max(record.getDurationMinutes(), 0))
                    .sum();
            records.stream()
                    .map(LearningRecord::getScore)
                    .filter(Objects::nonNull)
                    .map(BigDecimal::doubleValue)
                    .forEach(scores::add);
            subjects = resolveSubjectNames(records.stream()
                    .map(LearningRecord::getSubjectId)
                    .filter(Objects::nonNull)
                    .toList());
            masteredPointCount = countOf(progressMapper.selectCount(
                    new LambdaQueryWrapper<StudentProgress>()
                            .eq(StudentProgress::getUserId, userId)
                            .eq(StudentProgress::getMastered, ENABLED)
            ));
        }

        if (withGoals) {
            goalCount = countOf(goalMapper.selectCount(
                    new LambdaQueryWrapper<LearningGoal>().eq(LearningGoal::getUserId, userId)
            ));
            goalTitles = goalMapper.selectList(
                            new LambdaQueryWrapper<LearningGoal>()
                                    .eq(LearningGoal::getUserId, userId)
                                    .orderByDesc(LearningGoal::getId)
                                    .last("LIMIT " + MAX_GOAL_TITLES)
                    ).stream()
                    .map(LearningGoal::getTitle)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .toList();
            taskCount = countOf(taskMapper.selectCount(
                    new LambdaQueryWrapper<LearningTask>().eq(LearningTask::getUserId, userId)
            ));
            completedTaskCount = countOf(taskMapper.selectCount(
                    new LambdaQueryWrapper<LearningTask>()
                            .eq(LearningTask::getUserId, userId)
                            .eq(LearningTask::getStatus, "COMPLETED")
            ));
        }

        if (withGrades) {
            List<CloudClassSubmission> submissions = submissionMapper.selectList(
                    new LambdaQueryWrapper<CloudClassSubmission>()
                            .eq(CloudClassSubmission::getStudentId, userId)
                            .isNotNull(CloudClassSubmission::getScore)
                            .orderByDesc(CloudClassSubmission::getGradedAt)
                            .last("LIMIT " + SCAN_LIMIT)
            );
            gradedSubmissionCount = submissions.size();
            submissions.stream()
                    .map(CloudClassSubmission::getScore)
                    .filter(Objects::nonNull)
                    .map(BigDecimal::doubleValue)
                    .forEach(scores::add);
        }

        List<String> sources = new ArrayList<>();
        if (withRecords && learningRecordCount > 0) {
            sources.add("学习记录");
        }
        if (withRecords && masteredPointCount > 0) {
            sources.add("知识点掌握");
        }
        if (withGoals && goalCount > 0) {
            sources.add("学习目标");
        }
        if (withGoals && taskCount > 0) {
            sources.add("学习任务");
        }
        if (withGrades && gradedSubmissionCount > 0) {
            sources.add("云班级成绩");
        }
        if (hasSelfReport(profile)) {
            sources.add("自述资料");
        }

        return new CareerDataPreviewResponse(
                isOn(profile.getConsentGranted()),
                StringUtils.hasText(profile.getConsentVersion())
                        ? profile.getConsentVersion()
                        : CareerConsentPolicy.CONSENT_VERSION,
                CareerConsentPolicy.PRIVACY_NOTICE,
                withRecords,
                withGoals,
                withGrades,
                learningRecordCount,
                learningMinutes,
                masteredPointCount,
                goalCount,
                taskCount,
                completedTaskCount,
                gradedSubmissionCount,
                averageOf(scores),
                subjects,
                goalTitles,
                List.copyOf(sources)
        );
    }

    private List<String> resolveSubjectNames(List<Long> subjectIds) {
        Set<Long> distinctIds = new LinkedHashSet<>(subjectIds);
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        return subjectMapper.selectByIds(distinctIds).stream()
                .map(Subject::getName)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(MAX_SUBJECTS)
                .toList();
    }

    private Double averageOf(List<Double> scores) {
        if (scores.isEmpty()) {
            return null;
        }
        double average = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return Math.round(average * 10.0) / 10.0;
    }

    private boolean hasSelfReport(CareerProfile profile) {
        return StringUtils.hasText(profile.getMajor())
                || StringUtils.hasText(profile.getGradeLevel())
                || StringUtils.hasText(profile.getInterestTags())
                || StringUtils.hasText(profile.getStrengthNotes())
                || StringUtils.hasText(profile.getCareerGoal());
    }

    private boolean isOn(Integer value) {
        return value != null && value == ENABLED;
    }

    private int countOf(Long value) {
        return value == null ? 0 : value.intValue();
    }
}
