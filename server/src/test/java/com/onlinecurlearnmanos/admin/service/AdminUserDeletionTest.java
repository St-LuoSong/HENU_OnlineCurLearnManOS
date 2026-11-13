package com.onlinecurlearnmanos.admin.service;

import com.onlinecurlearnmanos.cloudclass.entity.CloudClass;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassMapper;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysRoleMapper;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminUserDeletionTest {

    private static final Long TARGET_ID = 11L;
    private static final Long OPERATOR_ID = 99L;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private AdminAuditService auditService;

    @Mock
    private CloudClassMapper cloudClassMapper;

    @InjectMocks
    private AdminUserService service;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(SysUser.class, CloudClass.class);
    }

    @Test
    void deletesStudentAndWritesDeletionAudit() {
        given(userMapper.selectById(TARGET_ID)).willReturn(user(TARGET_ID, "2024001", 1));
        given(roleMapper.selectRoleCodesByUserId(TARGET_ID)).willReturn(List.of("STUDENT"));
        given(userMapper.markAccountDeleted(
                eq(TARGET_ID),
                eq(OPERATOR_ID),
                eq("重复注册"),
                any(LocalDateTime.class)
        )).willReturn(1);

        service.deleteAccount(TARGET_ID, "重复注册", "2024001", OPERATOR_ID);

        verify(userMapper).markAccountDeleted(
                eq(TARGET_ID),
                eq(OPERATOR_ID),
                eq("重复注册"),
                any(LocalDateTime.class)
        );
        verify(auditService).record(
                eq(OPERATOR_ID),
                eq("USER_DELETE"),
                eq("SYS_USER"),
                eq(String.valueOf(TARGET_ID)),
                contains("2024001")
        );
    }

    @Test
    void rejectsTeacherWithActiveCloudClasses() {
        given(userMapper.selectById(TARGET_ID)).willReturn(user(TARGET_ID, "T2026001", 1));
        given(roleMapper.selectRoleCodesByUserId(TARGET_ID)).willReturn(List.of("TEACHER"));
        given(cloudClassMapper.selectCount(any())).willReturn(2L);

        assertThatThrownBy(() ->
                service.deleteAccount(TARGET_ID, "离职", "T2026001", OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 个进行中的云班级");

        verify(userMapper, never()).markAccountDeleted(any(), any(), any(), any());
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void allowsTeacherDeletionAfterActiveClassesAreArchived() {
        given(userMapper.selectById(TARGET_ID)).willReturn(user(TARGET_ID, "T2026001", 1));
        given(roleMapper.selectRoleCodesByUserId(TARGET_ID)).willReturn(List.of("TEACHER"));
        given(cloudClassMapper.selectCount(any())).willReturn(0L);
        given(userMapper.markAccountDeleted(
                eq(TARGET_ID),
                eq(OPERATOR_ID),
                eq("离职"),
                any(LocalDateTime.class)
        )).willReturn(1);

        service.deleteAccount(TARGET_ID, "离职", "T2026001", OPERATOR_ID);

        verify(userMapper).markAccountDeleted(
                eq(TARGET_ID),
                eq(OPERATOR_ID),
                eq("离职"),
                any(LocalDateTime.class)
        );
    }

    @Test
    void rejectsDeletingCurrentAdmin() {
        given(userMapper.selectById(OPERATOR_ID)).willReturn(user(OPERATOR_ID, "admin", 1));

        assertThatThrownBy(() ->
                service.deleteAccount(OPERATOR_ID, "误操作", "admin", OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除当前登录");

        verify(userMapper, never()).markAccountDeleted(any(), any(), any(), any());
    }

    @Test
    void rejectsDeletingLastActiveAdmin() {
        given(userMapper.selectById(TARGET_ID)).willReturn(user(TARGET_ID, "admin2", 1));
        given(roleMapper.selectRoleCodesByUserId(TARGET_ID)).willReturn(List.of("ADMIN"));
        given(roleMapper.countOtherActiveAdmins(TARGET_ID)).willReturn(0L);

        assertThatThrownBy(() ->
                service.deleteAccount(TARGET_ID, "清理", "admin2", OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少一个启用状态的管理员");

        verify(userMapper, never()).markAccountDeleted(any(), any(), any(), any());
    }

    @Test
    void rejectsMismatchedConfirmationLoginId() {
        given(userMapper.selectById(TARGET_ID)).willReturn(user(TARGET_ID, "2024001", 1));

        assertThatThrownBy(() ->
                service.deleteAccount(TARGET_ID, "违规", "2024002", OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("确认学号");

        verify(roleMapper, never()).selectRoleCodesByUserId(any());
        verify(userMapper, never()).markAccountDeleted(any(), any(), any(), any());
    }

    @Test
    void returnsConflictWhenAccountWasAlreadyDeletedByAnotherRequest() {
        given(userMapper.selectById(TARGET_ID)).willReturn(user(TARGET_ID, "2024001", 1));
        given(roleMapper.selectRoleCodesByUserId(TARGET_ID)).willReturn(List.of("STUDENT"));
        given(userMapper.markAccountDeleted(
                eq(TARGET_ID),
                eq(OPERATOR_ID),
                eq("违规"),
                any(LocalDateTime.class)
        )).willReturn(0);

        assertThatThrownBy(() ->
                service.deleteAccount(TARGET_ID, "违规", "2024001", OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已被删除");

        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    private SysUser user(Long id, String username, int status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName("测试用户");
        user.setStatus(status);
        user.setDeleted(0);
        user.setTokenVersion(3);
        return user;
    }
}
