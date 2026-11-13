package com.onlinecurlearnmanos.cloudclass.service;

import com.onlinecurlearnmanos.cloudclass.dto.CloudClassRosterResponse;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 班级名单的角色裁剪：教师 / 管理员拿完整统计，学生只拿身份信息。
 */
class CloudClassRosterPrivacyTest extends CloudClassServiceTestSupport {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Test
    void rosterIsForbiddenForNonMembers() {
        given(classMapper.selectById(CLASS_ID)).willReturn(cloudClass("ACTIVE"));
        given(memberMapper.selectCount(any())).willReturn(0L);

        assertThatThrownBy(() -> cloudClassService.roster(
                STUDENT_ID,
                List.of("STUDENT"),
                CLASS_ID,
                1,
                50
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void studentRosterOnlyExposesIdentityFields() throws Exception {
        given(classMapper.selectById(CLASS_ID)).willReturn(cloudClass("ACTIVE"));
        given(memberMapper.selectCount(any())).willReturn(1L);
        given(memberMapper.selectPage(any(), any()))
                .willAnswer(invocation -> memberPage(member("ACTIVE")));
        given(userMapper.selectByIdsIncludingDeleted(any())).willReturn(List.of(student()));

        CloudClassRosterResponse roster = cloudClassService.roster(
                STUDENT_ID,
                List.of("STUDENT"),
                CLASS_ID,
                1,
                50
        );

        assertThat(roster.readOnly()).isTrue();
        assertThat(roster.members()).hasSize(1);

        String json = objectMapper.writeValueAsString(roster.members().get(0));
        assertThat(json)
                .contains("\"loginId\":\"2024001\"")
                .contains("\"displayName\":\"张三\"")
                .contains("\"status\":\"ACTIVE\"")
                .doesNotContain("totalTaskCount")
                .doesNotContain("submittedTaskCount")
                .doesNotContain("overdueTaskCount")
                .doesNotContain("averageScore");
    }

    @Test
    void teacherRosterKeepsLearningStatistics() throws Exception {
        given(classMapper.selectById(CLASS_ID)).willReturn(cloudClass("ACTIVE"));
        given(memberMapper.selectCount(any())).willReturn(0L);
        given(memberMapper.selectPage(any(), any()))
                .willAnswer(invocation -> memberPage(member("ACTIVE")));
        given(taskMapper.selectList(any())).willReturn(List.of());
        given(userMapper.selectByIdsIncludingDeleted(any())).willReturn(List.of(student()));

        CloudClassRosterResponse roster = cloudClassService.roster(
                TEACHER_ID,
                List.of("TEACHER"),
                CLASS_ID,
                1,
                50
        );

        assertThat(roster.readOnly()).isFalse();
        assertThat(roster.members()).hasSize(1);
        assertThat(roster.members().get(0).totalTaskCount()).isZero();

        String json = objectMapper.writeValueAsString(roster.members().get(0));
        assertThat(json)
                .contains("\"totalTaskCount\":0")
                .contains("\"averageScore\":0.0");
    }
}
