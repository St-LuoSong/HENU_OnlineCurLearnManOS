package com.onlinecurlearnmanos.cloudclass.service;

import com.onlinecurlearnmanos.cloudclass.entity.CloudClass;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassMember;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassTask;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassMemberMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassResourceMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassSubmissionMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassSubmissionVersionMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassTaskMapper;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.learning.service.LearningRecordService;
import com.onlinecurlearnmanos.point.service.PointService;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 云班级服务单元测试公共装置。
 *
 * <p>只装配 Mock 依赖，不启动 Spring 上下文、不连接数据库，因此可在离线环境直接执行，
 * 用来固定云班级的名单可见性、加入策略与提交策略。</p>
 */
@ExtendWith(MockitoExtension.class)
abstract class CloudClassServiceTestSupport {

    protected static final Long CLASS_ID = 11L;
    protected static final Long TEACHER_ID = 21L;
    protected static final Long STUDENT_ID = 31L;
    protected static final Long TASK_ID = 41L;

    @Mock
    protected CloudClassMapper classMapper;

    @Mock
    protected CloudClassMemberMapper memberMapper;

    @Mock
    protected CloudClassResourceMapper resourceMapper;

    @Mock
    protected CloudClassTaskMapper taskMapper;

    @Mock
    protected CloudClassSubmissionMapper submissionMapper;

    @Mock
    protected CloudClassSubmissionVersionMapper submissionVersionMapper;

    @Mock
    protected SysUserMapper userMapper;

    @Mock
    protected SubjectMapper subjectMapper;

    @Mock
    protected KnowledgePointMapper knowledgePointMapper;

    @Mock
    protected LearningRecordService learningRecordService;

    @Mock
    protected PointService pointService;

    @InjectMocks
    protected CloudClassServiceImpl cloudClassService;

    protected CloudClass cloudClass(String status) {
        CloudClass cloudClass = new CloudClass();
        cloudClass.setId(CLASS_ID);
        cloudClass.setName("高等数学 A 班");
        cloudClass.setTeacherId(TEACHER_ID);
        cloudClass.setClassCode("ABC234");
        cloudClass.setStatus(status);
        cloudClass.setJoinEnabled(1);
        return cloudClass;
    }

    protected CloudClassMember member(String status) {
        CloudClassMember member = new CloudClassMember();
        member.setId(51L);
        member.setClassId(CLASS_ID);
        member.setStudentId(STUDENT_ID);
        member.setStatus(status);
        member.setJoinedAt(LocalDateTime.of(2026, 9, 1, 8, 0));
        return member;
    }

    protected CloudClassTask task(String status, LocalDateTime dueAt, int allowLateSubmit) {
        CloudClassTask task = new CloudClassTask();
        task.setId(TASK_ID);
        task.setClassId(CLASS_ID);
        task.setTeacherId(TEACHER_ID);
        task.setTitle("第一章作业");
        task.setTaskType("HOMEWORK");
        task.setStatus(status);
        task.setDueAt(dueAt);
        task.setAllowLateSubmit(allowLateSubmit);
        task.setRewardPoints(10);
        return task;
    }

    protected SysUser student() {
        SysUser user = new SysUser();
        user.setId(STUDENT_ID);
        user.setUsername("2024001");
        user.setDisplayName("张三");
        return user;
    }

    protected Page<CloudClassMember> memberPage(CloudClassMember... members) {
        Page<CloudClassMember> page = new Page<>(1, 50);
        page.setRecords(List.of(members));
        page.setTotal(members.length);
        return page;
    }
}
