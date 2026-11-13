package com.onlinecurlearnmanos.user.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.user.dto.UserProfileResponse;
import com.onlinecurlearnmanos.user.dto.UserProfileUpdateRequest;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.entity.UserProfile;
import com.onlinecurlearnmanos.user.mapper.SysRoleMapper;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.onlinecurlearnmanos.user.mapper.UserProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 学生 / 教师个人资料。登录标识（学号 / 工号）仍然只读，普通用户不能自行修改。
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final UserProfileMapper profileMapper;

    public UserProfileResponse get(Long userId) {
        return toResponse(requireUser(userId), findByUserId(userId));
    }

    @Transactional
    public UserProfileResponse update(Long userId, UserProfileUpdateRequest request) {
        SysUser user = requireUser(userId);
        String email = normalizeNullable(request.email());
        if (email != null && !email.equalsIgnoreCase(user.getEmail())) {
            long taken = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getEmail, email)
                    .ne(SysUser::getId, userId));
            if (taken > 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "该邮箱已被其他账号使用");
            }
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getDisplayName, request.displayName().trim())
                .set(SysUser::getEmail, email));

        UserProfile profile = findByUserId(userId);
        boolean creating = profile == null;
        if (creating) {
            profile = new UserProfile();
            profile.setUserId(userId);
            profile.setDeleted(0);
            profile.setVersion(0);
        }
        profile.setAvatarUrl(normalizeNullable(request.avatarUrl()));
        profile.setDepartment(normalizeNullable(request.department()));
        profile.setMajor(normalizeNullable(request.major()));
        profile.setGradeLevel(normalizeNullable(request.gradeLevel()));
        profile.setPhone(normalizeNullable(request.phone()));
        profile.setBio(normalizeNullable(request.bio()));
        profile.setTeacherTitle(normalizeNullable(request.teacherTitle()));
        profile.setResearchArea(normalizeNullable(request.researchArea()));
        profile.setNotifyEmail(Boolean.FALSE.equals(request.notifyEmail()) ? 0 : 1);
        profile.setNotifyForumReply(Boolean.FALSE.equals(request.notifyForumReply()) ? 0 : 1);
        if (creating) {
            profileMapper.insert(profile);
        } else {
            profileMapper.updateById(profile);
        }
        return toResponse(requireUser(userId), findByUserId(userId));
    }

    private UserProfile findByUserId(Long userId) {
        return profileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId)
                .last("LIMIT 1"));
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private UserProfileResponse toResponse(SysUser user, UserProfile profile) {
        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                primaryRole(roles),
                roles,
                profile == null ? null : profile.getAvatarUrl(),
                profile == null ? null : profile.getDepartment(),
                profile == null ? null : profile.getMajor(),
                profile == null ? null : profile.getGradeLevel(),
                profile == null ? null : profile.getPhone(),
                profile == null ? null : profile.getBio(),
                profile == null ? null : profile.getTeacherTitle(),
                profile == null ? null : profile.getResearchArea(),
                profile == null || !Integer.valueOf(0).equals(profile.getNotifyEmail()),
                profile == null || !Integer.valueOf(0).equals(profile.getNotifyForumReply()),
                profile == null ? null : profile.getUpdatedAt()
        );
    }

    private String primaryRole(List<String> roles) {
        if (roles.contains("ADMIN")) {
            return "ADMIN";
        }
        return roles.contains("TEACHER") ? "TEACHER" : "STUDENT";
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
