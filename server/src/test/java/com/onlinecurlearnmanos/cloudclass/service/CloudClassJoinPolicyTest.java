package com.onlinecurlearnmanos.cloudclass.service;

import com.onlinecurlearnmanos.cloudclass.dto.CloudClassJoinRequest;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassMember;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 凭班级码加入的策略：重复加入、资格暂停、已被移出都必须给出明确反馈。
 */
class CloudClassJoinPolicyTest extends CloudClassServiceTestSupport {

    private static final CloudClassJoinRequest REQUEST = new CloudClassJoinRequest("ABC234");

    @Test
    void rejectsDuplicateJoinOfActiveMember() {
        given(classMapper.selectOne(any())).willReturn(cloudClass("ACTIVE"));
        given(memberMapper.selectOne(any())).willReturn(member("ACTIVE"));

        assertThatThrownBy(() -> cloudClassService.join(STUDENT_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("已加入");
    }

    @Test
    void rejectsJoinOfSuspendedMember() {
        given(classMapper.selectOne(any())).willReturn(cloudClass("ACTIVE"));
        given(memberMapper.selectOne(any())).willReturn(member("SUSPENDED"));

        assertThatThrownBy(() -> cloudClassService.join(STUDENT_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂停");
    }

    @Test
    void rejectsJoinOfRemovedMember() {
        given(classMapper.selectOne(any())).willReturn(cloudClass("ACTIVE"));
        given(memberMapper.selectOne(any())).willReturn(member("REMOVED"));

        assertThatThrownBy(() -> cloudClassService.join(STUDENT_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN))
                .hasMessageContaining("移出");
    }

    @Test
    void rejectsJoinWhenTeacherClosedClassCodeEntry() {
        var closedClass = cloudClass("ACTIVE");
        closedClass.setJoinEnabled(0);
        given(classMapper.selectOne(any())).willReturn(closedClass);

        assertThatThrownBy(() -> cloudClassService.join(STUDENT_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("关闭");
    }

    @Test
    void firstJoinInsertsActiveMembership() {
        given(classMapper.selectOne(any())).willReturn(cloudClass("ACTIVE"));
        given(memberMapper.selectOne(any())).willReturn(null);
        given(userMapper.selectByIdIncludingDeleted(any())).willReturn(null);
        given(memberMapper.selectCount(any())).willReturn(0L);
        given(taskMapper.selectCount(any())).willReturn(0L);
        given(resourceMapper.selectCount(any())).willReturn(0L);

        cloudClassService.join(STUDENT_ID, REQUEST);

        ArgumentCaptor<CloudClassMember> captor = ArgumentCaptor.forClass(CloudClassMember.class);
        verify(memberMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(captor.getValue().getClassId()).isEqualTo(CLASS_ID);
    }
}
