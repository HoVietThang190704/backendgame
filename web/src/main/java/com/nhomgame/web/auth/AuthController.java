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
    public ResponseEntity<?> register(@Valid @RequestBody SignupRequest req) {
        User user = authService.register(req);
        return ResponseEntity.ok(new com.nhomgame.domain.auth.dto.UserResponse(user));
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest req) {
        // authenticate + generate tokens
        User user = authService.findByUsername(req.getUsername());
        if (user == null || !authService.checkPassword(req.getPassword(), user.getPassword())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken rt = authService.createRefreshToken(user.getId(), refreshExpirationDays);
        JwtResponse resp = new JwtResponse(accessToken, rt.getToken());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody TokenRefreshRequest req) {
        try {
            RefreshToken old = authService.verifyRefreshToken(req.getRefreshToken());
            // rotate: delete old and create new
            authService.deleteRefreshTokensForUser(old.getUserId());
            User user = authService.findById(old.getUserId());
            if (user == null) return ResponseEntity.badRequest().body("User not found");
            String newAccess = jwtService.generateAccessToken(user);
            RefreshToken newRefresh = authService.createRefreshToken(user.getId(), refreshExpirationDays);
            return ResponseEntity.ok(new JwtResponse(newAccess, newRefresh.getToken()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody TokenRefreshRequest req) {
        try {
            RefreshToken rt = authService.verifyRefreshToken(req.getRefreshToken());
            authService.deleteRefreshTokensForUser(rt.getUserId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
}
