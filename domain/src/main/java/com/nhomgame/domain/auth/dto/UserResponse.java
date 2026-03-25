package com.nhomgame.domain.auth.dto;

import java.time.Instant;
import java.util.Set;

import com.nhomgame.domain.auth.Role;

public class UserResponse {
    private String id;
    private String username;
    private String name;
    private String email;
    private Set<Role> roles;
    private String avatarUrl;
    private String rule;
    private String role;
    private Boolean isActive;
    private String currentMatchId;
    private int rank;
    private int wins;
    private int losses;
    private double winRate;
    private Instant createdAt;
    private Instant modifiedAt;
    private Instant lastLogin;

    public UserResponse() {}

    public UserResponse(String id, String username, String name, String email, Set<Role> roles, String avatarUrl, String rule, String role, Boolean isActive, String currentMatchId, int rank, int wins, int losses, double winRate, Instant createdAt, Instant modifiedAt, Instant lastLogin) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.email = email;
        this.roles = roles;
        this.avatarUrl = avatarUrl;
        this.rule = rule;
        this.role = role;
        this.isActive = isActive;
        this.currentMatchId = currentMatchId;
        this.rank = rank;
        this.wins = wins;
        this.losses = losses;
        this.winRate = winRate;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.lastLogin = lastLogin;
    }

    public UserResponse(com.nhomgame.domain.auth.User u) {
        this.id = u.getId();
        this.username = u.getUsername();
        this.name = u.getName();
        this.email = u.getEmail();
        this.roles = u.getRoles();
        this.avatarUrl = u.getAvatarUrl();
        this.rule = u.getRule();
        this.role = u.getRule();
        this.isActive = u.isActive();
        this.currentMatchId = u.getCurrentMatchId();
        this.rank = u.getRank();
        this.wins = u.getWins();
        this.losses = u.getLosses();
        this.winRate = u.getWinRate();
        this.createdAt = u.getCreatedAt();
        this.modifiedAt = u.getModifiedAt();
        this.lastLogin = u.getLastLogin();
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Set<Role> getRoles() { return roles; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getRule() { return rule; }
    public String getRole() { return role; }
    public Boolean getIsActive() { return isActive; }
    public String getCurrentMatchId() { return currentMatchId; }
    public int getRank() { return rank; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public double getWinRate() { return winRate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getModifiedAt() { return modifiedAt; }
    public Instant getLastLogin() { return lastLogin; }
}