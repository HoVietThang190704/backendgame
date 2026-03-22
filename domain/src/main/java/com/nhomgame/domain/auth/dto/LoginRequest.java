package com.nhomgame.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for login requests.
 */
public class LoginRequest {
    @NotBlank
    private String email;

    @NotBlank
    private String password;

    public LoginRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
