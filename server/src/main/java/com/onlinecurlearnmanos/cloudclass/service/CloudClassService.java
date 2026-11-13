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

import java.util.List;

public interface CloudClassService {

    CloudClassResponse create(Long teacherId, CloudClassCreateRequest request);

    CloudClassResponse join(Long studentId, CloudClassJoinRequest request);

    CloudClassResponse preview(String classCode);

    List<CloudClassResponse> listTeaching(Long teacherId);

    List<CloudClassResponse> listLearning(Long studentId);

    CloudClassDetailResponse detail(Long userId, List<String> roles, Long classId);

    CloudClassRosterResponse roster(
            Long userId,
            List<String> roles,
            Long classId,
            long page,
            long size
    );

    CloudClassResourceResponse publishResource(
            Long teacherId,
            Long classId,
            CloudClassResourceRequest request
    );

    CloudClassTaskResponse publishTask(
            Long teacherId,
            Long classId,
            CloudClassTaskRequest request
    );

    CloudClassSubmissionResponse submit(
            Long studentId,
            Long taskId,
            CloudClassSubmissionRequest request
    );

    List<CloudClassSubmissionResponse> submissions(Long teacherId, Long taskId);

    /**
     * 学生上传作业附件前的完整校验：必须是本人提交，且仍然满足班级状态、
     * 任务开放状态、截止时间与重交策略。返回访问上下文供附件服务使用。
     */
    CloudClassSubmissionAccess assertSubmissionAttachmentAllowed(Long studentId, Long submissionId);

    /**
     * 读取提交上下文并校验访问者身份（管理员 / 班级教师 / 提交者本人）。
     */
    CloudClassSubmissionAccess submissionAttachmentContext(
            Long userId,
            List<String> roles,
            Long submissionId
    );

    CloudClassSubmissionResponse grade(
            Long teacherId,
            Long submissionId,
            CloudClassGradeRequest request
    );

    CloudClassStatisticsResponse statistics(Long teacherId, Long classId);

    CloudClassResponse updateClassStatus(
            Long teacherId,
            Long classId,
            CloudClassStatusRequest request
    );

    CloudClassResponse updateJoinEnabled(
            Long teacherId,
            Long classId,
            boolean enabled
    );

    CloudClassTaskResponse updateTaskStatus(
            Long teacherId,
            Long taskId,
            CloudClassTaskStatusRequest request
    );

    List<CloudClassResponse> adminList(
            Long subjectId,
            Long teacherId,
            String status,
            String semester,
            String keyword
    );

    CloudClassResponse adminUpdateStatus(Long classId, CloudClassStatusRequest request);

    List<TeacherStudentResponse> teacherStudents(Long teacherId);

    CloudClassMemberResponse updateMemberStatus(
            Long teacherId,
            Long classId,
            Long studentId,
            CloudClassMemberStatusRequest request
    );
}
