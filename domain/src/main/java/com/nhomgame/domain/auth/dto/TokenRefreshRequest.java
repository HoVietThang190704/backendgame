package com.nhomgame.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for requesting a new access token using a refresh token.
 */
public class TokenRefreshRequest {
    @NotBlank
    private String refreshToken;

    public TokenRefreshRequest() {
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
