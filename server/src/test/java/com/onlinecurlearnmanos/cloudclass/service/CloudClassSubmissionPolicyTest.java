package com.onlinecurlearnmanos.cloudclass.service;

import com.onlinecurlearnmanos.cloudclass.dto.CloudClassSubmissionRequest;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 任务提交策略：班级状态、任务状态、成员资格与截止时间四道闸门。
 */
class CloudClassSubmissionPolicyTest extends CloudClassServiceTestSupport {

    private static final CloudClassSubmissionRequest REQUEST =
            new CloudClassSubmissionRequest("我的作业答案");

    @Test
    void rejectsSubmissionWhenClassIsClosed() {
        given(taskMapper.selectById(TASK_ID)).willReturn(task("PUBLISHED", null, 1));
        given(classMapper.selectById(CLASS_ID)).willReturn(cloudClass("CLOSED"));

        assertThatThrownBy(() -> cloudClassService.submit(STUDENT_ID, TASK_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("归档或关闭");
    }

    @Test
    void rejectsSubmissionWhenTaskIsDraft() {
        given(taskMapper.selectById(TASK_ID)).willReturn(task("DRAFT", null, 1));
        given(classMapper.selectById(CLASS_ID)).willReturn(cloudClass("ACTIVE"));

        assertThatThrownBy(() -> cloudClassService.submit(STUDENT_ID, TASK_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未开放提交");
    }

    @Test
    void rejectsSubmissionWhenStudentIsNotMember() {
        given(taskMapper.selectById(TASK_ID)).willReturn(task("PUBLISHED", null, 1));
        given(classMapper.selectById(CLASS_ID)).willReturn(cloudClass("ACTIVE"));
        given(memberMapper.selectCount(any())).willReturn(0L);

        assertThatThrownBy(() -> cloudClassService.submit(STUDENT_ID, TASK_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未加入");
    }

    @Test
    void rejectsLateSubmissionWhenLateSubmitIsDisabled() {
        given(taskMapper.selectById(TASK_ID)).willReturn(
                task("PUBLISHED", LocalDateTime.now().minusDays(1), 0)
        );
        given(classMapper.selectById(CLASS_ID)).willReturn(cloudClass("ACTIVE"));
        given(memberMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> cloudClassService.submit(STUDENT_ID, TASK_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("截止时间");
    }
}
