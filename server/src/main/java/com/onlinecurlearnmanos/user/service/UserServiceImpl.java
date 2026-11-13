package com.onlinecurlearnmanos.user.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.point.service.PointService;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysRoleMapper;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.onlinecurlearnmanos.user.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    /** 账号状态：1 正常，2 待管理员审核（教师自助注册后进入该状态）。 */
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_PENDING = 2;

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final PointService pointService;

    public UserServiceImpl(
            SysUserMapper userMapper,
            SysRoleMapper roleMapper,
            SysUserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder,
            PointService pointService
    ) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.pointService = pointService;
    }

    @Override
    @Transactional
    public SysUser register(
            String username,
            String rawPassword,
            String displayName,
            String email,
            String roleCode
    ) {
        if (findByUsername(username) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        String normalizedRole = roleCode == null ? "STUDENT" : roleCode.trim().toUpperCase();
        if (!"STUDENT".equals(normalizedRole) && !"TEACHER".equals(normalizedRole)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "注册角色不合法");
        }
        int status = "TEACHER".equals(normalizedRole) ? STATUS_PENDING : STATUS_ACTIVE;
        return createUser(username, rawPassword, displayName, email, normalizedRole, status);
    }

    @Override
    @Transactional
    public SysUser createUserIfAbsent(
            String username,
            String rawPassword,
            String displayName,
            String email,
            String roleCode
    ) {
        SysUser existing = findByUsername(username);
        if (existing != null) {
            return existing;
        }
        return createUser(username, rawPassword, displayName, email, roleCode, STATUS_ACTIVE);
    }

    @Override
    @Transactional
    public void assignRole(Long userId, String roleCode) {
        Long roleId = roleMapper.selectIdByCode(roleCode);
        if (roleId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "系统角色未初始化");
        }
        userRoleMapper.insert(userId, roleId);
    }

    private SysUser createUser(
            String username,
            String rawPassword,
            String displayName,
            String email,
            String roleCode,
            int status
    ) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setStatus(status);
        user.setDeleted(0);
        user.setVersion(0);
        userMapper.insert(user);
        assignRole(user.getId(), roleCode);
        pointService.initializeAccount(user.getId());
        return user;
    }

    private SysUser findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
    }
}
