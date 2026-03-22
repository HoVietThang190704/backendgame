package com.nhomgame.web.auth;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nhomgame.domain.auth.User;
import com.nhomgame.domain.auth.dto.UserResponse;
import com.nhomgame.service.auth.AuthService;
import com.nhomgame.web.dto.ApiResponse;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Object>> profile(@RequestParam(required = false) String fields, Principal principal) {
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(401).body(new ApiResponse<>(401, false, "Unauthorized", null));
        }

        String email = principal.getName();
        User user = authService.findByEmail(email);
        if (user == null) {
            return ResponseEntity.status(404).body(new ApiResponse<>(404, false, "User not found", null));
        }

        UserResponse userResponse = new UserResponse(user);

        if (fields == null || fields.isBlank()) {
            return ResponseEntity.ok(new ApiResponse<>(200, true, "User profile fetched", userResponse));
        }

        Set<String> wanted = Set.of(fields.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        Map<String, Object> filtered = new HashMap<>();

        // Always include id
        filtered.put("id", userResponse.getId());

        if (wanted.contains("username")) filtered.put("username", userResponse.getUsername());
        if (wanted.contains("email")) filtered.put("email", userResponse.getEmail());
        if (wanted.contains("name")) filtered.put("name", userResponse.getName());
        if (wanted.contains("role")) filtered.put("role", userResponse.getRole());
        if (wanted.contains("rule")) filtered.put("rule", userResponse.getRule());
        if (wanted.contains("avatar_url") || wanted.contains("avatarUrl")) filtered.put("avatar_url", userResponse.getAvatarUrl());
        if (wanted.contains("isActive") || wanted.contains("is_active")) filtered.put("isActive", userResponse.getIsActive());
        if (wanted.contains("currentMatchId") || wanted.contains("current_match_id")) filtered.put("currentMatchId", userResponse.getCurrentMatchId());
        if (wanted.contains("created") || wanted.contains("createdAt") || wanted.contains("created_at")) filtered.put("createdAt", userResponse.getCreatedAt());
        if (wanted.contains("modified") || wanted.contains("modifiedAt") || wanted.contains("modified_at")) filtered.put("modifiedAt", userResponse.getModifiedAt());
        if (wanted.contains("lastLogin") || wanted.contains("last_login")) filtered.put("lastLogin", userResponse.getLastLogin());

        // password not returned explicitly

        return ResponseEntity.ok(new ApiResponse<>(200, true, "User profile fetched", filtered));
    }
}
