package com.nhomgame.web.auth;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nhomgame.domain.auth.User;
import com.nhomgame.domain.auth.dto.UserResponse;
import com.nhomgame.service.auth.AuthService;
import com.nhomgame.web.dto.ApiResponse;

/**
 * REST Controller for user operations
 * Endpoint: /api/user
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AuthService authService;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserController.class);

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * GET /api/user/profile
     * 
     * Fetch user profile with optional field filtering
     * 
     * Authentication: Required (JWT Bearer token)
     * 
     * Query Parameters:
     *   fields: comma-separated list of fields to return
     *   Example: ?fields=id,username,email,rank,avatar_url
     * 
     * Response: 
     *   - If fields param not provided: Full user object
     *   - If fields param provided: Filtered object with only requested fields
     *   - Never returns: password, passwordHash
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Object>> profile(
            @RequestParam(required = false) String fields,
            Principal principal) {

        // Authenticate user from JWT token
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(HttpStatus.UNAUTHORIZED.value(), false, "Unauthorized", null));
        }

        String email = principal.getName();
        User user = authService.findByEmail(email);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), false, "User not found", null));
        }

        UserResponse userResponse = new UserResponse(user);
        log.info("User profile requested: {} with fields: {}", email, fields != null ? fields : "all");

        // If no fields specified, return full profile
        if (fields == null || fields.isBlank()) {
            return ResponseEntity.ok(new ApiResponse<>(200, true, "User profile fetched", userResponse));
        }

        // Parse requested fields and filter response
        Set<String> wantedFields = parseFields(fields);
        Map<String, Object> filtered = new HashMap<>();

        // Always include id
        filtered.put("id", userResponse.getId());

        // Map component field names (both camelCase and snake_case)
        FieldMapper mapper = new FieldMapper(userResponse);
        for (String field : wantedFields) {
            if (mapper.has(field)) {
                Object value = mapper.get(field);
                if (value != null) {
                    // Store using the canonical field name
                    filtered.put(mapper.getCanonical(field), value);
                }
            }
        }

        log.debug("Filtered fields returned: {}", filtered.keySet());
        return ResponseEntity.ok(new ApiResponse<>(200, true, "User profile fetched", filtered));
    }

    /**
     * Parse comma-separated fields string into a Set
     */
    private Set<String> parseFields(String fields) {
        return Set.of(fields.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Helper class to map field names to UserResponse getters
     * Supports both camelCase and snake_case naming conventions
     * Security: Never returns password field
     */
    private static class FieldMapper {
        private final UserResponse user;

        // Map of canonical field names to their getter
        private static final Map<String, String> FIELD_MAP = new HashMap<>();

        static {
            // ID field
            FIELD_MAP.put("id", "id");

            // User info
            FIELD_MAP.put("username", "username");
            FIELD_MAP.put("email", "email");
            FIELD_MAP.put("name", "name");
            FIELD_MAP.put("displayName", "name");
            FIELD_MAP.put("display_name", "name");

            // Avatar and appearance
            FIELD_MAP.put("avatar", "avatarUrl");
            FIELD_MAP.put("avatar_url", "avatarUrl");
            FIELD_MAP.put("avatarUrl", "avatarUrl");

            // Roles and permissions
            FIELD_MAP.put("role", "role");
            FIELD_MAP.put("rule", "rule");
            FIELD_MAP.put("roles", "roles");

            // Status
            FIELD_MAP.put("isActive", "isActive");
            FIELD_MAP.put("is_active", "isActive");
            FIELD_MAP.put("active", "isActive");

            // Match info
            FIELD_MAP.put("currentMatchId", "currentMatchId");
            FIELD_MAP.put("current_match_id", "currentMatchId");
            FIELD_MAP.put("matchId", "currentMatchId");

            // Ranking
            FIELD_MAP.put("rank", "rank");
            FIELD_MAP.put("ranking", "rank");

            // Timestamps
            FIELD_MAP.put("createdAt", "createdAt");
            FIELD_MAP.put("created_at", "createdAt");
            FIELD_MAP.put("created", "createdAt");

            FIELD_MAP.put("modifiedAt", "modifiedAt");
            FIELD_MAP.put("modified_at", "modifiedAt");
            FIELD_MAP.put("modified", "modifiedAt");
            FIELD_MAP.put("updatedAt", "modifiedAt");
            FIELD_MAP.put("updated_at", "modifiedAt");

            FIELD_MAP.put("lastLogin", "lastLogin");
            FIELD_MAP.put("last_login", "lastLogin");
        }

        public FieldMapper(UserResponse user) {
            this.user = user;
        }

        /**
         * Check if field is available (and not password)
         */
        public boolean has(String fieldName) {
            // NEVER allow password or passwordHash
            if (fieldName.equalsIgnoreCase("password")
                    || fieldName.equalsIgnoreCase("passwordHash")
                    || fieldName.equalsIgnoreCase("password_hash")) {
                return false;
            }
            return FIELD_MAP.containsKey(fieldName);
        }

        /**
         * Get field value by name
         */
        public Object get(String fieldName) {
            if (!has(fieldName)) {
                return null;
            }

            String canonical = FIELD_MAP.get(fieldName);
            return switch (canonical) {
                case "id" -> user.getId();
                case "username" -> user.getUsername();
                case "email" -> user.getEmail();
                case "name" -> user.getName();
                case "avatarUrl" -> user.getAvatarUrl();
                case "role" -> user.getRole();
                case "rule" -> user.getRule();
                case "roles" -> user.getRoles();
                case "isActive" -> user.getIsActive();
                case "currentMatchId" -> user.getCurrentMatchId();
                case "rank" -> user.getRank();
                case "createdAt" -> user.getCreatedAt();
                case "modifiedAt" -> user.getModifiedAt();
                case "lastLogin" -> user.getLastLogin();
                default -> null;
            };
        }

        /**
         * Get canonical field name (camelCase)
         */
        public String getCanonical(String fieldName) {
            if (!has(fieldName)) {
                return fieldName;
            }
            return FIELD_MAP.get(fieldName);
        }
    }
}
