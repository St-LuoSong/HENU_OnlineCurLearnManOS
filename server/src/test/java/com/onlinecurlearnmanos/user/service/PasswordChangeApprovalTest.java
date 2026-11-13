package com.onlinecurlearnmanos.user.service;

import com.onlinecurlearnmanos.admin.service.AdminAuditService;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import com.onlinecurlearnmanos.user.dto.PasswordChangeRequestResponse;
import com.onlinecurlearnmanos.user.dto.PasswordChangeReviewRequest;
import com.onlinecurlearnmanos.user.dto.PasswordChangeSubmitRequest;
import com.onlinecurlearnmanos.user.entity.PasswordChangeRequest;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.PasswordChangeRequestMapper;
import com.onlinecurlearnmanos.user.mapper.SysRoleMapper;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 密码变更审批状态机：提交需要验证当前密码与强度，管理员批准后新密码生效并自增会话版本。
 */
@ExtendWith(MockitoExtension.class)
class PasswordChangeApprovalTest {

    private static final Long USER_ID = 31L;
    private static final Long ADMIN_ID = 34L;
    private static final Long REQUEST_ID = 71L;
    private static final String OLD_HASH = "$2a$10$oldhash";

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private PasswordChangeRequestMapper requestMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AdminAuditService auditService;

    @InjectMocks
    private PasswordChangeService passwordChangeService;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(SysUser.class, PasswordChangeRequest.class);
    }

    @Test
    void submitRejectsWrongCurrentPassword() {
        given(userMapper.selectById(USER_ID)).willReturn(user());
        given(passwordEncoder.matches("wrong", OLD_HASH)).willReturn(false);

        assertThatThrownBy(() -> passwordChangeService.submit(
                USER_ID,
                new PasswordChangeSubmitRequest("wrong", "NewPassw0rd2")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(requestMapper, never()).insert(any(PasswordChangeRequest.class));
    }

    @Test
    void submitRejectsWeakPassword() {
        given(userMapper.selectById(USER_ID)).willReturn(user());
        given(passwordEncoder.matches("OldPassw0rd1", OLD_HASH)).willReturn(true);

        assertThatThrownBy(() -> passwordChangeService.submit(
                USER_ID,
                new PasswordChangeSubmitRequest("OldPassw0rd1", "onlyletters")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字母和数字");
    }

    @Test
    void submitRejectsSamePasswordAsCurrent() {
        given(userMapper.selectById(USER_ID)).willReturn(user());
        given(passwordEncoder.matches("OldPassw0rd1", OLD_HASH)).willReturn(true);
        given(passwordEncoder.matches("OldPassw0rd1", OLD_HASH)).willReturn(true);

        assertThatThrownBy(() -> passwordChangeService.submit(
                USER_ID,
                new PasswordChangeSubmitRequest("OldPassw0rd1", "OldPassw0rd1")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能与当前密码相同");
    }

    @Test
    void submitRejectsDuplicatePendingRequest() {
        given(userMapper.selectById(USER_ID)).willReturn(user());
        given(passwordEncoder.matches("OldPassw0rd1", OLD_HASH)).willReturn(true);
        given(passwordEncoder.matches("NewPassw0rd2", OLD_HASH)).willReturn(false);
        given(requestMapper.selectOne(any())).willReturn(pendingRequest());

        assertThatThrownBy(() -> passwordChangeService.submit(
                USER_ID,
                new PasswordChangeSubmitRequest("OldPassw0rd1", "NewPassw0rd2")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待审批");
    }

    @Test
    void submitStoresHashedPasswordOnly() {
        given(userMapper.selectById(USER_ID)).willReturn(user());
        given(passwordEncoder.matches("OldPassw0rd1", OLD_HASH)).willReturn(true);
        given(passwordEncoder.matches("NewPassw0rd2", OLD_HASH)).willReturn(false);
        given(requestMapper.selectOne(any())).willReturn(null);
        given(passwordEncoder.encode("NewPassw0rd2")).willReturn("$2a$10$newhash");
        given(requestMapper.insert(any(PasswordChangeRequest.class))).willAnswer(invocation -> {
            ((PasswordChangeRequest) invocation.getArgument(0)).setId(REQUEST_ID);
            return 1;
        });
        given(requestMapper.selectById(REQUEST_ID)).willReturn(pendingRequest());
        given(userMapper.selectByIdsIncludingDeleted(any())).willReturn(List.of(user()));
        given(roleMapper.selectRoleCodesByUserId(USER_ID)).willReturn(List.of("STUDENT"));

        passwordChangeService.submit(
                USER_ID,
                new PasswordChangeSubmitRequest("OldPassw0rd1", "NewPassw0rd2")
        );

        ArgumentCaptor<PasswordChangeRequest> captor =
                ArgumentCaptor.forClass(PasswordChangeRequest.class);
        verify(requestMapper).insert(captor.capture());
        assertThat(captor.getValue().getNewPasswordHash()).isEqualTo("$2a$10$newhash");
        assertThat(captor.getValue().getNewPasswordHash()).doesNotContain("NewPassw0rd2");
        assertThat(captor.getValue().getStatus())
                .isEqualTo(PasswordChangeRequest.STATUS_PENDING);
    }

    @Test
    void approveSwapsPasswordAndBumpsTokenVersion() {
        PasswordChangeRequest pending = pendingRequest();
        PasswordChangeRequest approved = pendingRequest();
        approved.setStatus(PasswordChangeRequest.STATUS_APPROVED);
        given(requestMapper.selectById(REQUEST_ID)).willReturn(pending, approved);
        given(requestMapper.update(isNull(), any())).willReturn(1);
        given(userMapper.update(isNull(), any())).willReturn(1);
        given(userMapper.selectByIdsIncludingDeleted(any())).willReturn(List.of(user()));
        given(roleMapper.selectRoleCodesByUserId(USER_ID)).willReturn(List.of("STUDENT"));

        PasswordChangeRequestResponse response = passwordChangeService.review(
                ADMIN_ID,
                REQUEST_ID,
                new PasswordChangeReviewRequest(true, "已核验身份")
        );

        assertThat(response.status()).isEqualTo(PasswordChangeRequest.STATUS_APPROVED);
        verify(requestMapper).update(isNull(), any());
        verify(userMapper).update(isNull(), any());
        verify(auditService).record(
                eq(ADMIN_ID),
                eq("PASSWORD_REQUEST_APPROVE"),
                eq("SYS_USER"),
                eq(String.valueOf(USER_ID)),
                any()
        );
    }

    @Test
    void rejectKeepsPasswordUntouched() {
        PasswordChangeRequest pending = pendingRequest();
        PasswordChangeRequest rejected = pendingRequest();
        rejected.setStatus(PasswordChangeRequest.STATUS_REJECTED);
        given(requestMapper.selectById(REQUEST_ID)).willReturn(pending, rejected);
        given(requestMapper.update(isNull(), any())).willReturn(1);
        given(userMapper.selectByIdsIncludingDeleted(any())).willReturn(List.of(user()));
        given(roleMapper.selectRoleCodesByUserId(USER_ID)).willReturn(List.of("STUDENT"));

        PasswordChangeRequestResponse response = passwordChangeService.review(
                ADMIN_ID,
                REQUEST_ID,
                new PasswordChangeReviewRequest(false, "新密码强度不足")
        );

        assertThat(response.status()).isEqualTo(PasswordChangeRequest.STATUS_REJECTED);
        verify(userMapper, never()).update(isNull(), any());
        verify(auditService).record(
                eq(ADMIN_ID),
                eq("PASSWORD_REQUEST_REJECT"),
                eq("SYS_USER"),
                eq(String.valueOf(USER_ID)),
                any()
        );
    }

    @Test
    void reviewRejectsAlreadyHandledRequest() {
        given(requestMapper.selectById(REQUEST_ID)).willReturn(pendingRequest());
        given(requestMapper.update(isNull(), any())).willReturn(0);

        assertThatThrownBy(() -> passwordChangeService.review(
                ADMIN_ID,
                REQUEST_ID,
                new PasswordChangeReviewRequest(true, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被处理");
        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    void userCanCancelOwnPendingRequest() {
        given(requestMapper.update(isNull(), any())).willReturn(1);

        passwordChangeService.cancel(USER_ID, REQUEST_ID);

        verify(requestMapper).update(isNull(), any());
    }

    @Test
    void cancelFailsWhenRequestIsNotPendingOrNotOwned() {
        given(requestMapper.update(isNull(), any())).willReturn(0);

        assertThatThrownBy(() -> passwordChangeService.cancel(USER_ID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被处理");
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("2024001");
        user.setDisplayName("张三");
        user.setPasswordHash(OLD_HASH);
        user.setStatus(1);
        user.setTokenVersion(0);
        return user;
    }

    private PasswordChangeRequest pendingRequest() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setId(REQUEST_ID);
        request.setUserId(USER_ID);
        request.setNewPasswordHash("$2a$10$newhash");
        request.setStatus(PasswordChangeRequest.STATUS_PENDING);
        request.setRequestedAt(LocalDateTime.of(2026, 9, 20, 18, 0));
        return request;
    }
}
