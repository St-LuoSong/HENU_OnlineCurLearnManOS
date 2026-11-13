package com.onlinecurlearnmanos.user.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import com.onlinecurlearnmanos.user.dto.UserProfileResponse;
import com.onlinecurlearnmanos.user.dto.UserProfileUpdateRequest;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.entity.UserProfile;
import com.onlinecurlearnmanos.user.mapper.SysRoleMapper;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.onlinecurlearnmanos.user.mapper.UserProfileMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 个人资料：登录标识只读，缺失资料返回默认值，首次保存自动建档，邮箱不可与他人冲突。
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final Long USER_ID = 31L;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private UserProfileMapper profileMapper;

    @InjectMocks
    private UserProfileService userProfileService;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(SysUser.class, UserProfile.class);
    }

    @Test
    void returnsDefaultsWhenProfileMissing() {
        given(userMapper.selectById(USER_ID)).willReturn(user());
        given(profileMapper.selectOne(any())).willReturn(null);
        given(roleMapper.selectRoleCodesByUserId(USER_ID)).willReturn(List.of("TEACHER"));

        UserProfileResponse response = userProfileService.get(USER_ID);

        assertThat(response.loginId()).isEqualTo("T2026001");
        assertThat(response.role()).isEqualTo("TEACHER");
        assertThat(response.notifyEmail()).isTrue();
        assertThat(response.notifyForumReply()).isTrue();
        assertThat(response.department()).isNull();
    }

    @Test
    void createsProfileOnFirstSave() {
        given(userMapper.selectById(USER_ID)).willReturn(user());
        given(profileMapper.selectOne(any())).willReturn(null);
        given(profileMapper.insert(any(UserProfile.class))).willReturn(1);
        given(userMapper.selectCount(any())).willReturn(0L);
        given(roleMapper.selectRoleCodesByUserId(USER_ID)).willReturn(List.of("TEACHER"));

        // 邮箱改为一个未被占用的新地址：唯一性检查通过，首次保存应插入资料行。
        userProfileService.update(USER_ID, updateRequest("T2026001.new@example.com", false));

        verify(profileMapper).insert(any(UserProfile.class));
        verify(userMapper).update(isNull(), any());
        verify(userMapper).selectCount(any());
    }

    @Test
    void updatesExistingProfileWithoutInsert() {
        given(userMapper.selectById(USER_ID)).willReturn(user());
        given(profileMapper.selectOne(any())).willReturn(existingProfile());
        given(roleMapper.selectRoleCodesByUserId(USER_ID)).willReturn(List.of("TEACHER"));

        userProfileService.update(USER_ID, updateRequest("T2026001@example.com", true));

        verify(profileMapper).updateById(any(UserProfile.class));
        verify(profileMapper, never()).insert(any(UserProfile.class));
    }

    @Test
    void rejectsEmailAlreadyUsedByAnotherAccount() {
        given(userMapper.selectById(USER_ID)).willReturn(user());
        given(userMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> userProfileService.update(
                USER_ID,
                updateRequest("taken@example.com", true)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
        verify(profileMapper, never()).insert(any(UserProfile.class));
    }

    private UserProfileUpdateRequest updateRequest(String email, boolean notifyEmail) {
        return new UserProfileUpdateRequest(
                "李老师",
                email,
                null,
                "计算机学院",
                null,
                null,
                "13800000000",
                "关注数据结构教学",
                "副教授",
                "分布式系统",
                notifyEmail,
                true
        );
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("T2026001");
        user.setDisplayName("李老师");
        user.setEmail("T2026001@example.com");
        user.setStatus(1);
        return user;
    }

    private UserProfile existingProfile() {
        UserProfile profile = new UserProfile();
        profile.setId(9L);
        profile.setUserId(USER_ID);
        profile.setNotifyEmail(1);
        profile.setNotifyForumReply(1);
        return profile;
    }
}
