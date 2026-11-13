package com.onlinecurlearnmanos.user.service;

import com.onlinecurlearnmanos.user.entity.SysUser;

public interface UserService {

    SysUser register(
            String username,
            String rawPassword,
            String displayName,
            String email,
            String roleCode
    );

    SysUser createUserIfAbsent(
            String username,
            String rawPassword,
            String displayName,
            String email,
            String roleCode
    );

    void assignRole(Long userId, String roleCode);
}
