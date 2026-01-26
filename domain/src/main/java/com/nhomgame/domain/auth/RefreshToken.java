package com.nhomgame.domain.auth;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Refresh token stored in DB for revocation/expiration tracking.
 */
@Document(collection = "refresh_tokens")
public class RefreshToken {
    @Id
    private String id;
    private String userId;
    private String token;
    private Instant expiryDate;
    private Instant createdAt = Instant.now();

    public RefreshToken() {
    }

    public RefreshToken(String userId, String token, Instant expiryDate) {
        this.userId = userId;
        this.token = token;
        this.expiryDate = expiryDate;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Instant expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
