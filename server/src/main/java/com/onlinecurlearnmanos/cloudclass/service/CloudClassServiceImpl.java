package com.onlinecurlearnmanos.cloudclass.service;

import com.onlinecurlearnmanos.cloudclass.dto.CloudClassCreateRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassDetailResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassGradeRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassJoinRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassMemberResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassMemberStatusRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassResourceRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassResourceResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassRosterMemberResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassRosterResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassStatisticsResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassStatusRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassSubmissionRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassSubmissionResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassSubmissionAccess;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassTaskRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassTaskResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassTaskStatusRequest;
import com.onlinecurlearnmanos.cloudclass.dto.TeacherStudentResponse;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClass;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassMember;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassResource;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassSubmission;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassSubmissionVersion;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassTask;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassMemberMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassResourceMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassSubmissionMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassSubmissionVersionMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassTaskMapper;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.learning.dto.LearningRecordResponse;
import com.onlinecurlearnmanos.learning.dto.LearningRecordSaveRequest;
import com.onlinecurlearnmanos.learning.service.LearningRecordService;
import com.onlinecurlearnmanos.point.service.PointService;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CloudClassServiceImpl implements CloudClassService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long MAX_ROSTER_PAGE_SIZE = 100;

    private final CloudClassMapper classMapper;
    private final CloudClassMemberMapper memberMapper;
    private final CloudClassResourceMapper resourceMapper;
    private final CloudClassTaskMapper taskMapper;
    private final CloudClassSubmissionMapper submissionMapper;
    private final CloudClassSubmissionVersionMapper submissionVersionMapper;
    private final SysUserMapper userMapper;
    private final SubjectMapper subjectMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final LearningRecordService learningRecordService;
    private final PointService pointService;

    @Override
    @Transactional
    public CloudClassResponse create(Long teacherId, CloudClassCreateRequest request) {
        Subject subject = subjectMapper.selectById(request.subjectId());
        if (subject == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        CloudClass cloudClass = new CloudClass();
        cloudClass.setSubjectId(subject.getId());
        cloudClass.setName(request.name().trim());
        cloudClass.setDescription(normalizeNullable(request.description()));
        cloudClass.setTeacherId(teacherId);
        cloudClass.setClassCode(generateUniqueCode());
        cloudClass.setAcademicYear(normalizeNullable(request.academicYear()));
        cloudClass.setSemester(normalizeNullable(request.semester()));
        cloudClass.setMaxMembers(request.maxMembers());
        cloudClass.setStatus("ACTIVE");
        cloudClass.setJoinEnabled(1);
        cloudClass.setDeleted(0);
        cloudClass.setVersion(0);
        classMapper.insert(cloudClass);
        return toClassResponse(cloudClass);
    }

    @Override
    @Transactional
    public CloudClassResponse join(Long studentId, CloudClassJoinRequest request) {
        CloudClass cloudClass = classMapper.selectOne(new LambdaQueryWrapper<CloudClass>()
                .eq(CloudClass::getClassCode, request.classCode().trim().toUpperCase())
                .eq(CloudClass::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (cloudClass == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "班级码不存在或班级已关闭");
        }
        if (!Integer.valueOf(1).equals(cloudClass.getJoinEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "教师已关闭该班级的班级码加入入口");
        }
        CloudClassMember existing = memberMapper.selectOne(new LambdaQueryWrapper<CloudClassMember>()
                .eq(CloudClassMember::getClassId, cloudClass.getId())
                .eq(CloudClassMember::getStudentId, studentId)
                .last("LIMIT 1"));
        if (existing == null) {
            if (cloudClass.getMaxMembers() != null) {
                long memberCount = memberMapper.selectCount(
                        new LambdaQueryWrapper<CloudClassMember>()
                                .eq(CloudClassMember::getClassId, cloudClass.getId())
                                .eq(CloudClassMember::getStatus, "ACTIVE")
                );
                if (memberCount >= cloudClass.getMaxMembers()) {
                    throw new BusinessException(
                            ErrorCode.CONFLICT,
                            "班级人数已满，请联系任课教师"
                    );
                }
            }
            CloudClassMember member = new CloudClassMember();
            member.setClassId(cloudClass.getId());
            member.setStudentId(studentId);
            member.setStatus("ACTIVE");
            memberMapper.insert(member);
        } else if ("ACTIVE".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "你已加入该云班级，无需重复加入");
        } else if ("SUSPENDED".equals(existing.getStatus())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "你的成员资格已被暂停，请联系任课教师"
            );
        } else {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "你已被移出该班级，请联系任课教师重新加入"
            );
        }
        return toClassResponse(cloudClass);
    }

    /**
     * 凭班级码预览班级信息，供学生在正式加入前确认，不写入成员关系。
     */
    @Override
    public CloudClassResponse preview(String classCode) {
        if (!StringUtils.hasText(classCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入班级码");
        }
        CloudClass cloudClass = classMapper.selectOne(new LambdaQueryWrapper<CloudClass>()
                .eq(CloudClass::getClassCode, classCode.trim().toUpperCase(Locale.ROOT))
                .last("LIMIT 1"));
        if (cloudClass == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "班级码不存在");
        }
        return toClassResponse(cloudClass);
    }

    @Override
    public List<CloudClassResponse> listTeaching(Long teacherId) {
        return classMapper.selectList(new LambdaQueryWrapper<CloudClass>()
                        .eq(CloudClass::getTeacherId, teacherId)
                        .orderByDesc(CloudClass::getUpdatedAt))
                .stream()
                .map(this::toClassResponse)
                .toList();
    }

    @Override
    public List<CloudClassResponse> listLearning(Long studentId) {
        List<Long> classIds = memberMapper.selectList(new LambdaQueryWrapper<CloudClassMember>()
                        .eq(CloudClassMember::getStudentId, studentId)
                        .eq(CloudClassMember::getStatus, "ACTIVE"))
                .stream()
                .map(CloudClassMember::getClassId)
                .toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        Map<Long, TaskProgress> progressByClass = loadStudentProgress(classIds, studentId);
        return classMapper.selectList(new LambdaQueryWrapper<CloudClass>()
                        .in(CloudClass::getId, classIds)
                        .orderByDesc(CloudClass::getUpdatedAt))
                .stream()
                .map(cloudClass -> toClassResponse(
                        cloudClass,
                        progressByClass.get(cloudClass.getId())
                ))
                .toList();
    }

    /**
     * 学生视角的班级进度：只查一次任务与本人提交，用于班级列表展示待提交/逾期。
     */
    private Map<Long, TaskProgress> loadStudentProgress(List<Long> classIds, Long studentId) {
        List<CloudClassTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<CloudClassTask>()
                .in(CloudClassTask::getClassId, classIds));
        if (tasks.isEmpty()) {
            return Map.of();
        }
        List<Long> taskIds = tasks.stream().map(CloudClassTask::getId).toList();
        List<CloudClassSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<CloudClassSubmission>()
                        .eq(CloudClassSubmission::getStudentId, studentId)
                        .in(CloudClassSubmission::getTaskId, taskIds)
        );
        Map<String, CloudClassSubmission> submissionIndex = new HashMap<>();
        for (CloudClassSubmission submission : submissions) {
            submissionIndex.put(
                    submission.getTaskId() + ":" + submission.getStudentId(),
                    submission
            );
        }
        Map<Long, TaskProgress> result = new HashMap<>();
        tasks.stream()
                .collect(Collectors.groupingBy(CloudClassTask::getClassId))
                .forEach((classId, classTasks) -> result.put(
                        classId,
                        progressOf(studentId, classTasks, submissionIndex)
                ));
        return result;
    }

    @Override
    public CloudClassDetailResponse detail(Long userId, List<String> roles, Long classId) {
        CloudClass cloudClass = getRequiredClass(classId);
        boolean admin = roles.contains("ADMIN");
        boolean teacher = Objects.equals(cloudClass.getTeacherId(), userId);
        boolean member = isMember(classId, userId);
        if (!admin && !teacher && !member) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该云班级");
        }
        List<CloudClassMemberResponse> members = admin || teacher
                ? loadMemberResponsesByClass(List.of(classId)).getOrDefault(classId, List.of())
                : List.of();
        List<CloudClassResourceResponse> resources = resourceMapper.selectList(
                        new LambdaQueryWrapper<CloudClassResource>()
                                .eq(CloudClassResource::getClassId, classId)
                                .orderByDesc(CloudClassResource::getCreatedAt))
                .stream()
                .map(this::toResourceResponse)
                .toList();
        List<CloudClassTaskResponse> tasks = taskMapper.selectList(
                        new LambdaQueryWrapper<CloudClassTask>()
                                .eq(CloudClassTask::getClassId, classId)
                                .orderByDesc(CloudClassTask::getCreatedAt))
                .stream()
                .map(task -> toTaskResponse(task, member ? userId : null))
                .toList();
        return new CloudClassDetailResponse(
                toClassResponse(cloudClass),
                members,
                resources,
                tasks
        );
    }

    /**
     * 班级名单。教师与管理员得到完整学习统计，学生只得到学号、姓名、加入时间与成员状态。
     *
     * <p>学生视角只返回在读（ACTIVE）成员，也不返回任何成绩、评语与学习活跃度，
     * 避免同班同学互相看到作业与分数。</p>
     */
    @Override
    public CloudClassRosterResponse roster(
            Long userId,
            List<String> roles,
            Long classId,
            long page,
            long size
    ) {
        CloudClass cloudClass = getRequiredClass(classId);
        boolean admin = roles.contains("ADMIN");
        boolean teacher = Objects.equals(cloudClass.getTeacherId(), userId);
        boolean member = isMember(classId, userId);
        if (!admin && !teacher && !member) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该云班级名单");
        }
        boolean readOnly = !admin && !teacher;
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_ROSTER_PAGE_SIZE);
        LambdaQueryWrapper<CloudClassMember> wrapper = new LambdaQueryWrapper<CloudClassMember>()
                .eq(CloudClassMember::getClassId, classId)
                .orderByAsc(CloudClassMember::getJoinedAt)
                .orderByAsc(CloudClassMember::getId);
        if (readOnly) {
            wrapper.eq(CloudClassMember::getStatus, "ACTIVE");
        }
        Page<CloudClassMember> pageResult = memberMapper.selectPage(
                new Page<>(safePage, safeSize),
                wrapper
        );
        List<CloudClassMember> records = pageResult.getRecords();
        List<CloudClassRosterMemberResponse> members = readOnly
                ? buildBasicRosterResponses(records)
                : buildMemberResponses(List.of(classId), records)
                        .getOrDefault(classId, List.of())
                        .stream()
                        .map(CloudClassRosterMemberResponse::fullView)
                        .toList();
        return new CloudClassRosterResponse(
                members,
                pageResult.getTotal(),
                safePage,
                safeSize,
                readOnly
        );
    }

    @Override
    @Transactional
    public CloudClassResourceResponse publishResource(
            Long teacherId,
            Long classId,
            CloudClassResourceRequest request
    ) {
        CloudClass cloudClass = getRequiredClass(classId);
        requireTeacher(cloudClass, teacherId);
        requireActiveClass(cloudClass);
        CloudClassResource resource = new CloudClassResource();
        resource.setClassId(classId);
        resource.setPublisherId(teacherId);
        resource.setTitle(request.title().trim());
        resource.setResourceType(request.resourceType().trim().toUpperCase());
        resource.setContent(normalizeNullable(request.content()));
        resource.setExternalUrl(normalizeNullable(request.externalUrl()));
        resource.setDeleted(0);
        resource.setVersion(0);
        resourceMapper.insert(resource);
        return toResourceResponse(resource);
    }

    @Override
    @Transactional
    public CloudClassTaskResponse publishTask(
            Long teacherId,
            Long classId,
            CloudClassTaskRequest request
    ) {
        CloudClass cloudClass = getRequiredClass(classId);
        requireTeacher(cloudClass, teacherId);
        requireActiveClass(cloudClass);
        validateKnowledgePoint(cloudClass, request.knowledgePointId());
        CloudClassTask task = new CloudClassTask();
        task.setClassId(classId);
        task.setKnowledgePointId(request.knowledgePointId());
        task.setTeacherId(teacherId);
        task.setTitle(request.title().trim());
        task.setDescription(normalizeNullable(request.description()));
        task.setTaskType(request.taskType().trim().toUpperCase());
        task.setDueAt(request.dueAt());
        task.setRewardPoints(request.rewardPoints() == null ? 0 : request.rewardPoints());
        task.setAllowResubmit(Boolean.TRUE.equals(request.allowResubmit()) ? 1 : 0);
        task.setAllowLateSubmit(Boolean.TRUE.equals(request.allowLateSubmit()) ? 1 : 0);
        task.setMaxScore(request.maxScore());
        String taskStatus = normalizeNewTaskStatus(request.status());
        task.setStatus(taskStatus);
        if ("PUBLISHED".equals(taskStatus)) {
            task.setPublishedAt(LocalDateTime.now());
        }
        task.setDeleted(0);
        task.setVersion(0);
        taskMapper.insert(task);
        return toTaskResponse(task, null);
    }

    @Override
    @Transactional
    public CloudClassSubmissionResponse submit(
            Long studentId,
            Long taskId,
            CloudClassSubmissionRequest request
    ) {
        CloudClassTask task = getRequiredTask(taskId);
        CloudClass cloudClass = getRequiredClass(task.getClassId());
        if (!"ACTIVE".equals(cloudClass.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "班级已归档或关闭，无法提交作业");
        }
        if (!"PUBLISHED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务未开放提交");
        }
        if (!isMember(task.getClassId(), studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "你尚未加入该云班级");
        }
        boolean late = task.getDueAt() != null && LocalDateTime.now().isAfter(task.getDueAt());
        if (late && !Integer.valueOf(1).equals(task.getAllowLateSubmit())) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务已过截止时间，不再接受提交");
        }
        CloudClassSubmission submission = submissionMapper.selectOne(
                new LambdaQueryWrapper<CloudClassSubmission>()
                        .eq(CloudClassSubmission::getTaskId, taskId)
                        .eq(CloudClassSubmission::getStudentId, studentId)
                        .last("LIMIT 1")
        );
        if (submission == null) {
            submission = new CloudClassSubmission();
            submission.setTaskId(taskId);
            submission.setStudentId(studentId);
            submission.setContent(request.content());
            submission.setStatus("SUBMITTED");
            submission.setCurrentVersion(1);
            submission.setSubmittedAt(LocalDateTime.now());
            submissionMapper.insert(submission);
        } else {
            if ("GRADED".equals(submission.getStatus())
                    && !Integer.valueOf(1).equals(task.getAllowResubmit())) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "作业已批改，教师未开放重交"
                );
            }
            int nextVersion = (submission.getCurrentVersion() == null
                    ? 1
                    : submission.getCurrentVersion()) + 1;
            submissionMapper.update(null, new LambdaUpdateWrapper<CloudClassSubmission>()
                    .eq(CloudClassSubmission::getId, submission.getId())
                    .set(CloudClassSubmission::getContent, request.content())
                    .set(CloudClassSubmission::getStatus, "SUBMITTED")
                    .set(CloudClassSubmission::getScore, null)
                    .set(CloudClassSubmission::getFeedback, null)
                    .set(CloudClassSubmission::getGradedAt, null)
                    .set(CloudClassSubmission::getSubmittedAt, LocalDateTime.now())
                    .set(CloudClassSubmission::getCurrentVersion, nextVersion));
        }
        CloudClassSubmission saved = getRequiredSubmissionByTaskAndStudent(taskId, studentId);
        recordSubmissionVersion(saved, task, late);
        return toSubmissionResponse(saved);
    }

    /**
     * 每次提交都保留一个版本，教师批改后学生重交也不会抹掉历史记录。
     */
    private void recordSubmissionVersion(
            CloudClassSubmission submission,
            CloudClassTask task,
            boolean late
    ) {
        CloudClassSubmissionVersion version = new CloudClassSubmissionVersion();
        version.setSubmissionId(submission.getId());
        version.setTaskId(task.getId());
        version.setStudentId(submission.getStudentId());
        version.setVersionNo(
                submission.getCurrentVersion() == null ? 1 : submission.getCurrentVersion()
        );
        version.setContent(submission.getContent());
        version.setIsLate(late ? 1 : 0);
        version.setStatus(submission.getStatus());
        version.setSubmittedAt(submission.getSubmittedAt());
        submissionVersionMapper.insert(version);
    }

    @Override
    public List<CloudClassSubmissionResponse> submissions(Long teacherId, Long taskId) {
        CloudClassTask task = getRequiredTask(taskId);
        requireTeacher(getRequiredClass(task.getClassId()), teacherId);
        return submissionMapper.selectList(new LambdaQueryWrapper<CloudClassSubmission>()
                        .eq(CloudClassSubmission::getTaskId, taskId)
                        .orderByDesc(CloudClassSubmission::getSubmittedAt))
                .stream()
                .map(this::toSubmissionResponse)
                .toList();
    }

    @Override
    public CloudClassSubmissionAccess assertSubmissionAttachmentAllowed(
            Long studentId,
            Long submissionId
    ) {
        CloudClassSubmission submission = getRequiredSubmission(submissionId);
        if (!Objects.equals(submission.getStudentId(), studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能为自己的作业上传附件");
        }
        CloudClassTask task = getRequiredTask(submission.getTaskId());
        CloudClass cloudClass = getRequiredClass(task.getClassId());
        if (!"ACTIVE".equals(cloudClass.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "班级已归档或关闭，无法继续提交作业附件");
        }
        if (!"PUBLISHED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务未开放提交");
        }
        if (!isMember(task.getClassId(), studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "你尚未加入该云班级");
        }
        boolean late = task.getDueAt() != null && LocalDateTime.now().isAfter(task.getDueAt());
        if (late && !Integer.valueOf(1).equals(task.getAllowLateSubmit())) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务已过截止时间，不再接受提交");
        }
        if ("GRADED".equals(submission.getStatus())
                && !Integer.valueOf(1).equals(task.getAllowResubmit())) {
            throw new BusinessException(ErrorCode.CONFLICT, "作业已批改，教师未开放重交");
        }
        return new CloudClassSubmissionAccess(
                submission.getId(),
                task.getId(),
                cloudClass.getId(),
                cloudClass.getTeacherId(),
                submission.getStudentId(),
                submission.getCurrentVersion() == null ? 1 : submission.getCurrentVersion(),
                submission.getStatus()
        );
    }

    @Override
    public CloudClassSubmissionAccess submissionAttachmentContext(
            Long userId,
            List<String> roles,
            Long submissionId
    ) {
        CloudClassSubmission submission = getRequiredSubmission(submissionId);
        CloudClassTask task = getRequiredTask(submission.getTaskId());
        CloudClass cloudClass = getRequiredClass(task.getClassId());
        boolean admin = roles != null && roles.contains("ADMIN");
        boolean teacher = Objects.equals(cloudClass.getTeacherId(), userId);
        boolean owner = Objects.equals(submission.getStudentId(), userId);
        if (!admin && !teacher && !owner) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该作业的附件");
        }
        return new CloudClassSubmissionAccess(
                submission.getId(),
                task.getId(),
                cloudClass.getId(),
                cloudClass.getTeacherId(),
                submission.getStudentId(),
                submission.getCurrentVersion() == null ? 1 : submission.getCurrentVersion(),
                submission.getStatus()
        );
    }

    @Override
    @Transactional
    public CloudClassSubmissionResponse grade(
            Long teacherId,
            Long submissionId,
            CloudClassGradeRequest request
    ) {
        CloudClassSubmission submission = getRequiredSubmission(submissionId);
        CloudClassTask task = getRequiredTask(submission.getTaskId());
        CloudClass cloudClass = getRequiredClass(task.getClassId());
        requireTeacher(cloudClass, teacherId);
        if (task.getMaxScore() != null && request.score().compareTo(task.getMaxScore()) > 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "分数不能高于任务满分 " + task.getMaxScore()
            );
        }
        LocalDateTime gradedAt = LocalDateTime.now();
        submissionMapper.update(null, new LambdaUpdateWrapper<CloudClassSubmission>()
                .eq(CloudClassSubmission::getId, submissionId)
                .set(CloudClassSubmission::getScore, request.score())
                .set(CloudClassSubmission::getFeedback, normalizeNullable(request.feedback()))
                .set(CloudClassSubmission::getStatus, "GRADED")
                .set(CloudClassSubmission::getGradedAt, gradedAt));
        submissionVersionMapper.update(null, new LambdaUpdateWrapper<CloudClassSubmissionVersion>()
                .eq(CloudClassSubmissionVersion::getSubmissionId, submissionId)
                .eq(
                        CloudClassSubmissionVersion::getVersionNo,
                        submission.getCurrentVersion() == null
                                ? 1
                                : submission.getCurrentVersion()
                )
                .set(CloudClassSubmissionVersion::getScore, request.score())
                .set(
                        CloudClassSubmissionVersion::getFeedback,
                        normalizeNullable(request.feedback())
                )
                .set(CloudClassSubmissionVersion::getStatus, "GRADED")
                .set(CloudClassSubmissionVersion::getGradedAt, gradedAt));
        syncLearningClosure(submission, cloudClass, task, request);
        return toSubmissionResponse(getRequiredSubmission(submissionId));
    }

    @Override
    public CloudClassStatisticsResponse statistics(Long teacherId, Long classId) {
        CloudClass cloudClass = getRequiredClass(classId);
        requireTeacher(cloudClass, teacherId);
        List<Long> taskIds = taskMapper.selectList(new LambdaQueryWrapper<CloudClassTask>()
                        .eq(CloudClassTask::getClassId, classId))
                .stream()
                .map(CloudClassTask::getId)
                .toList();
        long memberCount = memberMapper.selectCount(new LambdaQueryWrapper<CloudClassMember>()
                .eq(CloudClassMember::getClassId, classId)
                .eq(CloudClassMember::getStatus, "ACTIVE"));
        if (taskIds.isEmpty()) {
            return new CloudClassStatisticsResponse(memberCount, 0, 0, 0, 0);
        }
        List<CloudClassSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<CloudClassSubmission>()
                        .in(CloudClassSubmission::getTaskId, taskIds)
        );
        long gradedCount = submissions.stream()
                .filter(item -> "GRADED".equals(item.getStatus()))
                .count();
        double averageScore = submissions.stream()
                .map(CloudClassSubmission::getScore)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);
        return new CloudClassStatisticsResponse(
                memberCount,
                taskIds.size(),
                submissions.size(),
                gradedCount,
                round(averageScore)
        );
    }

    @Override
    public List<CloudClassResponse> adminList(
            Long subjectId,
            Long teacherId,
            String status,
            String semester,
            String keyword
    ) {
        LambdaQueryWrapper<CloudClass> wrapper = new LambdaQueryWrapper<CloudClass>()
                .orderByDesc(CloudClass::getUpdatedAt);
        if (subjectId != null) {
            wrapper.eq(CloudClass::getSubjectId, subjectId);
        }
        if (teacherId != null) {
            wrapper.eq(CloudClass::getTeacherId, teacherId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(CloudClass::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(semester)) {
            wrapper.eq(CloudClass::getSemester, semester.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String normalized = keyword.trim();
            wrapper.and(condition -> condition
                    .like(CloudClass::getName, normalized)
                    .or()
                    .like(CloudClass::getClassCode, normalized));
        }
        return classMapper.selectList(wrapper)
                .stream()
                .map(this::toClassResponse)
                .toList();
    }

    @Override
    @Transactional
    public CloudClassResponse updateClassStatus(
            Long teacherId,
            Long classId,
            CloudClassStatusRequest request
    ) {
        CloudClass cloudClass = getRequiredClass(classId);
        requireTeacher(cloudClass, teacherId);
        return applyClassStatus(cloudClass, request.status());
    }

    @Override
    @Transactional
    public CloudClassResponse updateJoinEnabled(Long teacherId, Long classId, boolean enabled) {
        CloudClass cloudClass = getRequiredClass(classId);
        requireTeacher(cloudClass, teacherId);
        classMapper.update(null, new LambdaUpdateWrapper<CloudClass>()
                .eq(CloudClass::getId, classId)
                .set(CloudClass::getJoinEnabled, enabled ? 1 : 0));
        cloudClass.setJoinEnabled(enabled ? 1 : 0);
        return toClassResponse(cloudClass);
    }

    @Override
    @Transactional
    public CloudClassResponse adminUpdateStatus(Long classId, CloudClassStatusRequest request) {
        return applyClassStatus(getRequiredClass(classId), request.status());
    }

    @Override
    @Transactional
    public CloudClassTaskResponse updateTaskStatus(
            Long teacherId,
            Long taskId,
            CloudClassTaskStatusRequest request
    ) {
        CloudClassTask task = getRequiredTask(taskId);
        requireTeacher(getRequiredClass(task.getClassId()), teacherId);
        String normalized = normalizeTaskLifecycleStatus(request.status());
        LambdaUpdateWrapper<CloudClassTask> update = new LambdaUpdateWrapper<CloudClassTask>()
                .eq(CloudClassTask::getId, taskId)
                .set(CloudClassTask::getStatus, normalized);
        if ("DRAFT".equals(normalized)) {
            update.set(CloudClassTask::getPublishedAt, null);
            update.set(CloudClassTask::getClosedAt, null);
        } else if ("PUBLISHED".equals(normalized)) {
            update.set(
                    CloudClassTask::getPublishedAt,
                    task.getPublishedAt() == null ? LocalDateTime.now() : task.getPublishedAt()
            );
            update.set(CloudClassTask::getClosedAt, null);
        } else {
            update.set(
                    CloudClassTask::getClosedAt,
                    task.getClosedAt() == null ? LocalDateTime.now() : task.getClosedAt()
            );
        }
        taskMapper.update(null, update);
        return toTaskResponse(getRequiredTask(taskId), null);
    }

    private CloudClassResponse applyClassStatus(CloudClass cloudClass, String status) {
        String normalized = normalizeClassStatus(status);
        classMapper.update(null, new LambdaUpdateWrapper<CloudClass>()
                .eq(CloudClass::getId, cloudClass.getId())
                .set(CloudClass::getStatus, normalized));
        cloudClass.setStatus(normalized);
        return toClassResponse(cloudClass);
    }

    @Override
    public List<TeacherStudentResponse> teacherStudents(Long teacherId) {
        List<CloudClass> classes = classMapper.selectList(new LambdaQueryWrapper<CloudClass>()
                .eq(CloudClass::getTeacherId, teacherId)
                .orderByDesc(CloudClass::getUpdatedAt));
        if (classes.isEmpty()) {
            return List.of();
        }
        List<Long> classIds = classes.stream().map(CloudClass::getId).toList();
        Map<Long, List<CloudClassMemberResponse>> membersByClass =
                loadMemberResponsesByClass(classIds);
        List<TeacherStudentResponse> result = new ArrayList<>();
        for (CloudClass cloudClass : classes) {
            for (CloudClassMemberResponse member
                    : membersByClass.getOrDefault(cloudClass.getId(), List.of())) {
                result.add(new TeacherStudentResponse(
                        cloudClass.getId(),
                        cloudClass.getName(),
                        member.studentId(),
                        member.loginId(),
                        member.displayName(),
                        member.status(),
                        member.totalTaskCount(),
                        member.submittedTaskCount(),
                        member.pendingTaskCount(),
                        member.overdueTaskCount(),
                        member.gradedCount(),
                        member.averageScore()
                ));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public CloudClassMemberResponse updateMemberStatus(
            Long teacherId,
            Long classId,
            Long studentId,
            CloudClassMemberStatusRequest request
    ) {
        CloudClass cloudClass = getRequiredClass(classId);
        requireTeacher(cloudClass, teacherId);
        String normalized = request.status().trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "SUSPENDED", "REMOVED").contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "成员状态只能是 ACTIVE、SUSPENDED 或 REMOVED");
        }
        CloudClassMember member = getRequiredMember(classId, studentId);
        memberMapper.update(null, new LambdaUpdateWrapper<CloudClassMember>()
                .eq(CloudClassMember::getId, member.getId())
                .set(CloudClassMember::getStatus, normalized));
        return loadMemberResponsesByClass(List.of(classId))
                .getOrDefault(classId, List.of())
                .stream()
                .filter(item -> item.studentId().equals(studentId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "成员不存在"));
    }

    private CloudClassMember getRequiredMember(Long classId, Long studentId) {
        CloudClassMember member = memberMapper.selectOne(new LambdaQueryWrapper<CloudClassMember>()
                .eq(CloudClassMember::getClassId, classId)
                .eq(CloudClassMember::getStudentId, studentId)
                .last("LIMIT 1"));
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该学生不在班级中");
        }
        return member;
    }

    private CloudClass getRequiredClass(Long id) {
        CloudClass cloudClass = classMapper.selectById(id);
        if (cloudClass == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "云班级不存在");
        }
        return cloudClass;
    }

    private CloudClassTask getRequiredTask(Long id) {
        CloudClassTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    private CloudClassSubmission getRequiredSubmission(Long id) {
        CloudClassSubmission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交记录不存在");
        }
        return submission;
    }

    private CloudClassSubmission getRequiredSubmissionByTaskAndStudent(Long taskId, Long studentId) {
        CloudClassSubmission submission = submissionMapper.selectOne(
                new LambdaQueryWrapper<CloudClassSubmission>()
                        .eq(CloudClassSubmission::getTaskId, taskId)
                        .eq(CloudClassSubmission::getStudentId, studentId)
                        .last("LIMIT 1")
        );
        if (submission == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交记录不存在");
        }
        return submission;
    }

    private void requireTeacher(CloudClass cloudClass, Long teacherId) {
        if (!Objects.equals(cloudClass.getTeacherId(), teacherId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有班级教师可以执行该操作");
        }
    }

    private boolean isMember(Long classId, Long studentId) {
        return memberMapper.selectCount(new LambdaQueryWrapper<CloudClassMember>()
                .eq(CloudClassMember::getClassId, classId)
                .eq(CloudClassMember::getStudentId, studentId)
                .eq(CloudClassMember::getStatus, "ACTIVE")) > 0;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder builder = new StringBuilder(6);
            for (int index = 0; index < 6; index++) {
                builder.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            String code = builder.toString();
            if (classMapper.selectCount(new LambdaQueryWrapper<CloudClass>()
                    .eq(CloudClass::getClassCode, code)) == 0) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "班级码生成失败");
    }

    private void requireActiveClass(CloudClass cloudClass) {
        if (!"ACTIVE".equals(cloudClass.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "班级已归档或关闭，无法继续维护教学内容");
        }
    }

    private String normalizeNewTaskStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "PUBLISHED";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "PUBLISHED").contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新任务状态只能是 DRAFT 或 PUBLISHED");
        }
        return normalized;
    }

    private String normalizeTaskLifecycleStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "PUBLISHED", "CLOSED", "ARCHIVED").contains(normalized)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "任务状态只能是 DRAFT、PUBLISHED、CLOSED 或 ARCHIVED"
            );
        }
        return normalized;
    }

    private String normalizeClassStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "ARCHIVED", "CLOSED").contains(normalized)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "班级状态只能是 ACTIVE、ARCHIVED 或 CLOSED"
            );
        }
        return normalized;
    }

    private void validateKnowledgePoint(CloudClass cloudClass, Long knowledgePointId) {
        if (knowledgePointId == null) {
            return;
        }
        if (cloudClass.getSubjectId() == null) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "请先为班级设置课程，再关联知识点"
            );
        }
        KnowledgePoint point = knowledgePointMapper.selectById(knowledgePointId);
        if (point == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识点不存在");
        }
        if (!cloudClass.getSubjectId().equals(point.getSubjectId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知识点不属于本班级课程");
        }
    }

    /**
     * 批改后把结果写回学习记录与学习进度，并在达标时发放任务奖励积分。
     * 第一次批改创建学习记录，重复批改只更新同一条记录，不会重复累计次数；
     * 奖励积分使用固定幂等键，重复批改不会重复发放。
     */
    private void syncLearningClosure(
            CloudClassSubmission submission,
            CloudClass cloudClass,
            CloudClassTask task,
            CloudClassGradeRequest request
    ) {
        boolean mastered = isMastered(request.score(), task.getMaxScore());
        if (cloudClass.getSubjectId() != null) {
            String remark = cloudClass.getName() + " · " + task.getTitle();
            Long recordId = submission.getLearningRecordId();
            if (recordId == null) {
                LearningRecordResponse record = learningRecordService.create(
                        submission.getStudentId(),
                        new LearningRecordSaveRequest(
                                cloudClass.getSubjectId(),
                                task.getKnowledgePointId(),
                                null,
                                "HOMEWORK",
                                0,
                                request.score(),
                                mastered,
                                remark,
                                LocalDateTime.now()
                        )
                );
                submissionMapper.update(null, new LambdaUpdateWrapper<CloudClassSubmission>()
                        .eq(CloudClassSubmission::getId, submission.getId())
                        .set(CloudClassSubmission::getLearningRecordId, record.id()));
            } else {
                learningRecordService.updateScore(recordId, request.score(), mastered, remark);
            }
        }

        int reward = task.getRewardPoints() == null ? 0 : task.getRewardPoints();
        if (mastered && reward > 0) {
            pointService.award(
                    submission.getStudentId(),
                    reward,
                    "CLASS_TASK_REWARD",
                    "class-task-reward:" + task.getId() + ":" + submission.getStudentId(),
                    "CLOUD_CLASS_TASK",
                    String.valueOf(task.getId()),
                    "完成云班级任务奖励：" + task.getTitle()
            );
        }
    }

    private boolean isMastered(BigDecimal score, BigDecimal maxScore) {
        if (score == null) {
            return false;
        }
        BigDecimal full = maxScore == null ? BigDecimal.valueOf(100) : maxScore;
        return score.compareTo(full.multiply(BigDecimal.valueOf(0.6))) >= 0;
    }

    private CloudClassResponse toClassResponse(CloudClass cloudClass) {
        return toClassResponse(cloudClass, null);
    }

    private CloudClassResponse toClassResponse(CloudClass cloudClass, TaskProgress studentProgress) {
        SysUser teacher = userMapper.selectByIdIncludingDeleted(cloudClass.getTeacherId());
        Subject subject = cloudClass.getSubjectId() == null
                ? null
                : subjectMapper.selectById(cloudClass.getSubjectId());
        long memberCount = memberMapper.selectCount(new LambdaQueryWrapper<CloudClassMember>()
                .eq(CloudClassMember::getClassId, cloudClass.getId())
                .eq(CloudClassMember::getStatus, "ACTIVE"));
        long taskCount = taskMapper.selectCount(new LambdaQueryWrapper<CloudClassTask>()
                .eq(CloudClassTask::getClassId, cloudClass.getId()));
        long resourceCount = resourceMapper.selectCount(new LambdaQueryWrapper<CloudClassResource>()
                .eq(CloudClassResource::getClassId, cloudClass.getId()));
        return new CloudClassResponse(
                cloudClass.getId(),
                cloudClass.getSubjectId(),
                subject == null ? null : subject.getName(),
                cloudClass.getName(),
                cloudClass.getDescription(),
                cloudClass.getTeacherId(),
                teacher == null ? null : teacher.getDisplayName(),
                cloudClass.getClassCode(),
                cloudClass.getAcademicYear(),
                cloudClass.getSemester(),
                cloudClass.getMaxMembers(),
                cloudClass.getStatus(),
                Integer.valueOf(1).equals(cloudClass.getJoinEnabled()),
                memberCount,
                taskCount,
                resourceCount,
                cloudClass.getCreatedAt(),
                studentProgress == null ? 0 : studentProgress.pending(),
                studentProgress == null ? 0 : studentProgress.overdue(),
                studentProgress == null ? 0 : studentProgress.graded(),
                studentProgress == null || studentProgress.graded() == 0
                        ? null
                        : studentProgress.averageScore()
        );
    }

    /**
     * 一次性加载多个班级的成员名单与学习统计，避免按成员逐个查询造成 N+1。
     */
    private Map<Long, List<CloudClassMemberResponse>> loadMemberResponsesByClass(
            List<Long> classIds
    ) {
        if (classIds.isEmpty()) {
            return Map.of();
        }
        List<CloudClassMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<CloudClassMember>()
                        .in(CloudClassMember::getClassId, classIds)
                        .orderByAsc(CloudClassMember::getJoinedAt)
        );
        if (members.isEmpty()) {
            return Map.of();
        }
        return buildMemberResponses(classIds, members);
    }

    /**
     * 批量组装成员名单与学习统计，固定 4 次查询，避免逐成员逐任务查询造成 N+1。
     * {@code members} 允许只是某个班级的一页数据，{@code classIds} 决定任务统计范围。
     */
    private Map<Long, List<CloudClassMemberResponse>> buildMemberResponses(
            List<Long> classIds,
            List<CloudClassMember> members
    ) {
        if (classIds.isEmpty() || members.isEmpty()) {
            return Map.of();
        }
        List<CloudClassTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<CloudClassTask>()
                        .in(CloudClassTask::getClassId, classIds)
        );
        List<Long> taskIds = tasks.stream().map(CloudClassTask::getId).toList();
        List<CloudClassSubmission> submissions = taskIds.isEmpty()
                ? List.of()
                : submissionMapper.selectList(new LambdaQueryWrapper<CloudClassSubmission>()
                        .in(CloudClassSubmission::getTaskId, taskIds));
        Map<Long, List<CloudClassTask>> tasksByClass = tasks.stream()
                .collect(Collectors.groupingBy(CloudClassTask::getClassId));
        Map<String, CloudClassSubmission> submissionIndex = new HashMap<>();
        for (CloudClassSubmission submission : submissions) {
            submissionIndex.put(
                    submission.getTaskId() + ":" + submission.getStudentId(),
                    submission
            );
        }
        List<Long> studentIds = members.stream()
                .map(CloudClassMember::getStudentId)
                .distinct()
                .toList();
        Map<Long, SysUser> students = userMapper.selectByIdsIncludingDeleted(studentIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        Map<Long, List<CloudClassMemberResponse>> result = new HashMap<>();
        for (CloudClassMember member : members) {
            TaskProgress progress = progressOf(
                    member.getStudentId(),
                    tasksByClass.getOrDefault(member.getClassId(), List.of()),
                    submissionIndex
            );
            SysUser student = students.get(member.getStudentId());
            result.computeIfAbsent(member.getClassId(), key -> new ArrayList<>())
                    .add(new CloudClassMemberResponse(
                            member.getStudentId(),
                            student == null ? null : student.getUsername(),
                            student == null ? null : student.getDisplayName(),
                            member.getStatus(),
                            progress.total(),
                            progress.submitted(),
                            progress.pending(),
                            progress.overdue(),
                            progress.graded(),
                            progress.averageScore(),
                            member.getJoinedAt()
                    ));
        }
        return result;
    }

    /**
     * 学生视角的班级名单：只有身份信息，不返回任何学习统计。
     */
    private List<CloudClassRosterMemberResponse> buildBasicRosterResponses(
            List<CloudClassMember> members
    ) {
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = members.stream()
                .map(CloudClassMember::getStudentId)
                .distinct()
                .toList();
        Map<Long, SysUser> students = userMapper.selectByIdsIncludingDeleted(studentIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        List<CloudClassRosterMemberResponse> result = new ArrayList<>(members.size());
        for (CloudClassMember member : members) {
            SysUser student = students.get(member.getStudentId());
            result.add(CloudClassRosterMemberResponse.basicView(
                    member.getStudentId(),
                    student == null ? null : student.getUsername(),
                    student == null ? null : student.getDisplayName(),
                    member.getStatus(),
                    member.getJoinedAt()
            ));
        }
        return result;
    }

    private TaskProgress progressOf(
            Long studentId,
            List<CloudClassTask> tasks,
            Map<String, CloudClassSubmission> submissionIndex
    ) {
        long submitted = 0;
        long graded = 0;
        long overdue = 0;
        double scoreSum = 0;
        long scoreCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (CloudClassTask task : tasks) {
            CloudClassSubmission submission = submissionIndex.get(task.getId() + ":" + studentId);
            if (submission == null) {
                if (task.getDueAt() != null && now.isAfter(task.getDueAt())) {
                    overdue++;
                }
                continue;
            }
            submitted++;
            if ("GRADED".equals(submission.getStatus())) {
                graded++;
            }
            if (submission.getScore() != null) {
                scoreSum += submission.getScore().doubleValue();
                scoreCount++;
            }
        }
        long total = tasks.size();
        return new TaskProgress(
                total,
                submitted,
                total - submitted,
                overdue,
                graded,
                scoreCount == 0 ? 0 : round(scoreSum / scoreCount)
        );
    }

    private record TaskProgress(
            long total,
            long submitted,
            long pending,
            long overdue,
            long graded,
            double averageScore
    ) {
    }

    private CloudClassResourceResponse toResourceResponse(CloudClassResource resource) {
        SysUser publisher = userMapper.selectByIdIncludingDeleted(resource.getPublisherId());
        return new CloudClassResourceResponse(
                resource.getId(),
                resource.getTitle(),
                resource.getResourceType(),
                resource.getContent(),
                resource.getExternalUrl(),
                publisher == null ? null : publisher.getDisplayName(),
                resource.getCreatedAt()
        );
    }

    private CloudClassTaskResponse toTaskResponse(CloudClassTask task, Long studentId) {
        long submissionCount = submissionMapper.selectCount(new LambdaQueryWrapper<CloudClassSubmission>()
                .eq(CloudClassSubmission::getTaskId, task.getId()));
        CloudClassSubmission mySubmission = studentId == null
                ? null
                : submissionMapper.selectOne(new LambdaQueryWrapper<CloudClassSubmission>()
                        .eq(CloudClassSubmission::getTaskId, task.getId())
                        .eq(CloudClassSubmission::getStudentId, studentId)
                        .last("LIMIT 1"));
        KnowledgePoint point = task.getKnowledgePointId() == null
                ? null
                : knowledgePointMapper.selectById(task.getKnowledgePointId());
        return new CloudClassTaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getTaskType(),
                task.getKnowledgePointId(),
                point == null ? null : point.getName(),
                task.getDueAt(),
                task.getRewardPoints() == null ? 0 : task.getRewardPoints(),
                Integer.valueOf(1).equals(task.getAllowResubmit()),
                Integer.valueOf(1).equals(task.getAllowLateSubmit()),
                task.getMaxScore() == null ? null : task.getMaxScore().doubleValue(),
                task.getStatus(),
                submissionCount,
                mySubmission == null ? null : mySubmission.getId(),
                mySubmission == null ? null : mySubmission.getStatus(),
                mySubmission == null || mySubmission.getScore() == null
                        ? null
                        : mySubmission.getScore().doubleValue()
        );
    }

    private CloudClassSubmissionResponse toSubmissionResponse(CloudClassSubmission submission) {
        CloudClassTask task = taskMapper.selectById(submission.getTaskId());
        SysUser student = userMapper.selectByIdIncludingDeleted(submission.getStudentId());
        return new CloudClassSubmissionResponse(
                submission.getId(),
                submission.getTaskId(),
                task == null ? null : task.getTitle(),
                submission.getStudentId(),
                student == null ? null : student.getDisplayName(),
                submission.getContent(),
                submission.getScore(),
                submission.getFeedback(),
                submission.getStatus(),
                submission.getSubmittedAt(),
                submission.getGradedAt()
        );
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
