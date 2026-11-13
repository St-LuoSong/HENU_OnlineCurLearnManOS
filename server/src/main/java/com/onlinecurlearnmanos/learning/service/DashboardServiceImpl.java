package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.cloudclass.dto.CloudClassResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassStatisticsResponse;
import com.onlinecurlearnmanos.cloudclass.dto.TeacherStudentResponse;
import com.onlinecurlearnmanos.cloudclass.service.CloudClassService;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.learning.dto.DashboardOverviewResponse;
import com.onlinecurlearnmanos.learning.dto.DashboardSummaryResponse;
import com.onlinecurlearnmanos.learning.dto.InteractionOverviewResponse;
import com.onlinecurlearnmanos.learning.dto.ProgressResponse;
import com.onlinecurlearnmanos.learning.dto.RecentScoreResponse;
import com.onlinecurlearnmanos.learning.dto.RiskStudentResponse;
import com.onlinecurlearnmanos.learning.dto.StudentCourseCardResponse;
import com.onlinecurlearnmanos.learning.dto.StudentDashboardSummaryResponse;
import com.onlinecurlearnmanos.learning.dto.TeacherClassSummaryResponse;
import com.onlinecurlearnmanos.learning.dto.TeacherDashboardSummaryResponse;
import com.onlinecurlearnmanos.learning.entity.LearningGoal;
import com.onlinecurlearnmanos.learning.entity.LearningTask;
import com.onlinecurlearnmanos.learning.entity.StudentProgress;
import com.onlinecurlearnmanos.learning.mapper.LearningGoalMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningTaskMapper;
import com.onlinecurlearnmanos.learning.mapper.StudentProgressMapper;
import com.onlinecurlearnmanos.practice.entity.PracticeSet;
import com.onlinecurlearnmanos.practice.mapper.PracticeSetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int COURSE_CARD_LIMIT = 6;
    private static final int RISK_STUDENT_LIMIT = 10;
    private static final double RISK_SCORE_THRESHOLD = 60.0;

    private final LearningGoalMapper goalMapper;
    private final LearningTaskMapper taskMapper;
    private final StudentProgressMapper progressMapper;
    private final CloudClassService cloudClassService;
    private final ProgressService progressService;
    private final PracticeSetMapper practiceSetMapper;
    private final StudentActivityService studentActivityService;
    private final SubjectMapper subjectMapper;

    @Override
    public DashboardSummaryResponse summary(Long userId) {
        long activeGoals = goalMapper.selectCount(new LambdaQueryWrapper<LearningGoal>()
                .eq(LearningGoal::getUserId, userId)
                .eq(LearningGoal::getStatus, "ACTIVE"));
        long completedGoals = goalMapper.selectCount(new LambdaQueryWrapper<LearningGoal>()
                .eq(LearningGoal::getUserId, userId)
                .eq(LearningGoal::getStatus, "COMPLETED"));
        long pendingTasks = taskMapper.selectCount(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId)
                .ne(LearningTask::getStatus, "COMPLETED")
                .ne(LearningTask::getStatus, "CANCELLED"));

        LocalDateTime startOfWeek = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();
        LocalDateTime endOfWeek = startOfWeek.plusWeeks(1);
        long dueThisWeek = taskMapper.selectCount(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId)
                .ne(LearningTask::getStatus, "COMPLETED")
                .ne(LearningTask::getStatus, "CANCELLED")
                .ge(LearningTask::getDueAt, startOfWeek)
                .lt(LearningTask::getDueAt, endOfWeek));

        long masteredPoints = progressMapper.selectCount(new LambdaQueryWrapper<StudentProgress>()
                .eq(StudentProgress::getUserId, userId)
                .eq(StudentProgress::getMastered, 1));

        return new DashboardSummaryResponse(
                activeGoals,
                completedGoals,
                pendingTasks,
                dueThisWeek,
                masteredPoints
        );
    }

    @Override
    public DashboardOverviewResponse overview(Long userId, List<String> roles) {
        boolean teacher = roles != null && roles.contains("TEACHER");
        if (teacher) {
            return new DashboardOverviewResponse("TEACHER", null, teacherOverview(userId));
        }
        return new DashboardOverviewResponse("STUDENT", studentOverview(userId), null);
    }

    private StudentDashboardSummaryResponse studentOverview(Long userId) {
        List<CloudClassResponse> classes = cloudClassService.listLearning(userId);
        List<String> teacherNames = classes.stream()
                .map(CloudClassResponse::teacherName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<String> classNames = classes.stream()
                .map(CloudClassResponse::name)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<ProgressResponse> progressList = classes.isEmpty()
                ? List.of()
                : progressService.list(userId);
        List<PracticeSet> practiceSets = classes.isEmpty()
                ? List.of()
                : practiceSetMapper.selectList(new LambdaQueryWrapper<PracticeSet>()
                        .eq(PracticeSet::getUserId, userId)
                        .eq(PracticeSet::getStatus, "SUBMITTED")
                        .orderByDesc(PracticeSet::getSubmittedAt)
                        .last("LIMIT 100"));
        InteractionOverviewResponse interaction = studentActivityService.overview(userId);
        List<RecentScoreResponse> recentScores = studentActivityService.recentScores(userId);
        return new StudentDashboardSummaryResponse(
                summary(userId),
                teacherNames,
                classNames,
                interaction,
                recentScores,
                buildCourseCards(classes, progressList, practiceSets)
        );
    }

    private List<StudentCourseCardResponse> buildCourseCards(
            List<CloudClassResponse> classes,
            List<ProgressResponse> progressList,
            List<PracticeSet> practiceSets
    ) {
        Map<Long, CourseAccumulator> courses = new LinkedHashMap<>();
        for (CloudClassResponse cloudClass : classes) {
            if (cloudClass.subjectId() == null) {
                continue;
            }
            CourseAccumulator course = courses.computeIfAbsent(
                    cloudClass.subjectId(),
                    id -> new CourseAccumulator(
                            id,
                            StringUtils.hasText(cloudClass.subjectName())
                                    ? cloudClass.subjectName()
                                    : subjectName(id)
                    )
            );
            course.className = StringUtils.hasText(course.className)
                    ? course.className
                    : cloudClass.name();
            course.teacherName = StringUtils.hasText(course.teacherName)
                    ? course.teacherName
                    : cloudClass.teacherName();
            course.pendingTaskCount += cloudClass.myPendingTaskCount();
            if (cloudClass.myAverageScore() != null) {
                course.averageScore = cloudClass.myAverageScore();
            }
            course.lastActivityAt = later(course.lastActivityAt, cloudClass.createdAt());
        }
        for (ProgressResponse progress : progressList) {
            if (progress.subjectId() == null) {
                continue;
            }
            CourseAccumulator course = courses.get(progress.subjectId());
            if (course == null) {
                continue;
            }
            course.totalPoints++;
            if (progress.mastered()) {
                course.masteredPoints++;
            }
            course.lastActivityAt = later(course.lastActivityAt, progress.lastActivity());
        }
        for (PracticeSet practiceSet : practiceSets) {
            CourseAccumulator course = courses.get(practiceSet.getSubjectId());
            if (course == null) {
                continue;
            }
            course.lastActivityAt = later(course.lastActivityAt, practiceSet.getSubmittedAt());
        }
        return courses.values().stream()
                .sorted(Comparator.comparing(
                        CourseAccumulator::lastActivityForSort,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(COURSE_CARD_LIMIT)
                .map(CourseAccumulator::toResponse)
                .toList();
    }

    private String subjectName(Long subjectId) {
        Subject subject = subjectMapper.selectById(subjectId);
        return subject == null ? "未命名课程" : subject.getName();
    }

    private LocalDateTime later(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private TeacherDashboardSummaryResponse teacherOverview(Long teacherId) {
        List<CloudClassResponse> classes = cloudClassService.listTeaching(teacherId);
        List<TeacherStudentResponse> students = cloudClassService.teacherStudents(teacherId);
        long studentCount = students.stream()
                .map(TeacherStudentResponse::studentId)
                .distinct()
                .count();
        long pendingSubmissionCount = 0;
        long gradedSubmissionCount = 0;
        double weightedScore = 0;
        long scoreWeight = 0;
        List<TeacherClassSummaryResponse> classSummaries = new ArrayList<>();

        for (CloudClassResponse cloudClass : classes) {
            CloudClassStatisticsResponse statistics =
                    cloudClassService.statistics(teacherId, cloudClass.id());
            long pending = Math.max(0, statistics.submissionCount() - statistics.gradedCount());
            pendingSubmissionCount += pending;
            gradedSubmissionCount += statistics.gradedCount();
            if (statistics.gradedCount() > 0) {
                weightedScore += statistics.averageScore() * statistics.gradedCount();
                scoreWeight += statistics.gradedCount();
            }
            classSummaries.add(new TeacherClassSummaryResponse(
                    cloudClass.id(),
                    cloudClass.name(),
                    cloudClass.subjectName(),
                    statistics.memberCount(),
                    statistics.taskCount(),
                    pending,
                    statistics.gradedCount(),
                    statistics.averageScore()
            ));
        }

        double averageScore = scoreWeight == 0
                ? 0.0
                : Math.round(weightedScore / scoreWeight * 100.0) / 100.0;
        return new TeacherDashboardSummaryResponse(
                classes.size(),
                studentCount,
                pendingSubmissionCount,
                gradedSubmissionCount,
                averageScore,
                classSummaries,
                buildRiskStudents(students)
        );
    }

    private List<RiskStudentResponse> buildRiskStudents(List<TeacherStudentResponse> students) {
        return students.stream()
                .filter(student -> student.overdueTaskCount() > 0
                        || (student.gradedCount() > 0
                        && student.averageScore() < RISK_SCORE_THRESHOLD))
                .sorted(Comparator
                        .comparingLong(TeacherStudentResponse::overdueTaskCount)
                        .reversed()
                        .thenComparingDouble(TeacherStudentResponse::averageScore))
                .limit(RISK_STUDENT_LIMIT)
                .map(student -> new RiskStudentResponse(
                        student.classId(),
                        student.className(),
                        student.studentId(),
                        student.displayName(),
                        student.overdueTaskCount(),
                        student.pendingTaskCount(),
                        student.averageScore()
                ))
                .toList();
    }

    private static final class CourseAccumulator {

        private final Long subjectId;
        private String subjectName;
        private String className;
        private String teacherName;
        private int totalPoints;
        private int masteredPoints;
        private long pendingTaskCount;
        private Double averageScore;
        private LocalDateTime lastActivityAt;

        private CourseAccumulator(Long subjectId, String subjectName) {
            this.subjectId = subjectId;
            this.subjectName = subjectName;
        }

        private LocalDateTime lastActivityForSort() {
            return lastActivityAt;
        }

        private StudentCourseCardResponse toResponse() {
            int percent = totalPoints == 0
                    ? 0
                    : (int) Math.round(masteredPoints * 100.0 / totalPoints);
            String nextAction = pendingTaskCount > 0
                    ? "完成班级任务"
                    : totalPoints > 0 ? "继续练习" : "浏览学习资源";
            return new StudentCourseCardResponse(
                    subjectId,
                    StringUtils.hasText(subjectName) ? subjectName : "未命名课程",
                    className,
                    teacherName,
                    percent,
                    masteredPoints,
                    totalPoints,
                    pendingTaskCount,
                    averageScore,
                    lastActivityAt,
                    nextAction
            );
        }
    }
}
