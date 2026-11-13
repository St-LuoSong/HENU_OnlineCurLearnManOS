package com.onlinecurlearnmanos.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String displayName;
    private final String email;
    private final Integer status;
    private final List<String> roles;
    private final int tokenVersion;

    public UserPrincipal(
            Long id,
            String username,
            String password,
            String displayName,
            String email,
            Integer status,
            List<String> roles
    ) {
        this(id, username, password, displayName, email, status, roles, 0);
    }

    public UserPrincipal(
            Long id,
            String username,
            String password,
            String displayName,
            String email,
            Integer status,
            List<String> roles,
            int tokenVersion
    ) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.email = email;
        this.status = status;
        this.roles = List.copyOf(roles);
        this.tokenVersion = tokenVersion;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public List<String> getRoles() {
        return roles;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
