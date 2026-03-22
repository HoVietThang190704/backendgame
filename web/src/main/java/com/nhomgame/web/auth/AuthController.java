package com.nhomgame.web.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nhomgame.domain.auth.RefreshToken;
import com.nhomgame.domain.auth.User;
import com.nhomgame.domain.auth.dto.JwtResponse;
import com.nhomgame.domain.auth.dto.LoginRequest;
import com.nhomgame.domain.auth.dto.SignupRequest;
import com.nhomgame.domain.auth.dto.TokenRefreshRequest;
import com.nhomgame.service.auth.AuthService;
import com.nhomgame.service.auth.JwtService;
import com.nhomgame.web.dto.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    // read refresh token validity from properties (days)
    @Value("${jwt.refreshExpirationDays:7}")
    private long refreshExpirationDays;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<com.nhomgame.domain.auth.dto.UserResponse>> register(@Valid @RequestBody SignupRequest req) {
        User user = authService.register(req);
        com.nhomgame.domain.auth.dto.UserResponse resp = new com.nhomgame.domain.auth.dto.UserResponse(user);
        return ResponseEntity.status(201).body(new ApiResponse<>(201, true, "User registered successfully", resp));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> login(@Valid @RequestBody LoginRequest req) {
        String email = req.getEmail();
        if (email == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, false, "Invalid email or password", null));
        }
        User user = authService.findByEmail(email);
        if (user == null || !authService.checkPassword(req.getPassword(), user.getPassword())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(401, false, "Invalid email or password", null));
        }
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken rt = authService.createRefreshToken(user.getId(), refreshExpirationDays);
        JwtResponse jwtResp = new JwtResponse(accessToken, rt.getToken());
        return ResponseEntity.ok(new ApiResponse<>(200, true, "Login successful", jwtResp));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<JwtResponse>> refresh(@Valid @RequestBody TokenRefreshRequest req) {
        try {
            RefreshToken old = authService.verifyRefreshToken(req.getRefreshToken());
            // rotate: delete old and create new
            String userId = old.getUserId();
            if (userId == null) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(400, false, "Invalid refresh token", null));
            }
            authService.deleteRefreshTokensForUser(userId);
            User user = authService.findById(userId);
            if (user == null) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(400, false, "User not found", null));
            }
            String newAccess = jwtService.generateAccessToken(user);
            RefreshToken newRefresh = authService.createRefreshToken(user.getId(), refreshExpirationDays);
            JwtResponse jwtResp = new JwtResponse(newAccess, newRefresh.getToken());
            return ResponseEntity.ok(new ApiResponse<>(200, true, "Token refreshed", jwtResp));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, false, ex.getMessage(), null));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody TokenRefreshRequest req) {
        try {
            RefreshToken rt = authService.verifyRefreshToken(req.getRefreshToken());
            String userId = rt.getUserId();
            if (userId == null) {
                return ResponseEntity.badRequest().body(new ApiResponse<>(400, false, "Invalid refresh token", null));
            }
            authService.deleteRefreshTokensForUser(userId);
            return ResponseEntity.ok(new ApiResponse<>(200, true, "Logout successful", null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, false, ex.getMessage(), null));
        }
    }
}

