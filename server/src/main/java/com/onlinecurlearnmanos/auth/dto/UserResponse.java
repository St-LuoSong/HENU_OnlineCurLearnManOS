package com.onlinecurlearnmanos.auth.dto;

import com.onlinecurlearnmanos.security.UserPrincipal;

import java.util.List;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        String email,
        List<String> roles
) {

    public static UserResponse from(UserPrincipal principal) {
        return new UserResponse(
                principal.getId(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getEmail(),
                principal.getRoles()
        );
    }
}
