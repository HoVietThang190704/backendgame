package com.nhomgame.domain.auth.dto;

import java.time.Instant;
import java.util.Set;

import com.nhomgame.domain.auth.Role;

public class UserResponse {
    private String id;
    private String username;
    private String email;
    private Set<Role> roles;
    private Instant createdAt;
    private Instant lastLogin;

    public UserResponse() {}

    public UserResponse(String id, String username, String email, Set<Role> roles, Instant createdAt, Instant lastLogin) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.roles = roles;
        this.createdAt = createdAt;
        this.lastLogin = lastLogin;
    }

    public UserResponse(com.nhomgame.domain.auth.User u) {
        this.id = u.getId();
        this.username = u.getUsername();
        this.email = u.getEmail();
        this.roles = u.getRoles();
        this.createdAt = u.getCreatedAt();
        this.lastLogin = u.getLastLogin();
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public Set<Role> getRoles() { return roles; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLogin() { return lastLogin; }
} 