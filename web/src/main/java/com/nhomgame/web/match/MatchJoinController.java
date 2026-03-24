package com.nhomgame.web.match;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.nhomgame.domain.auth.User;
import com.nhomgame.domain.match.Match;
import com.nhomgame.service.auth.AuthService;
import com.nhomgame.service.match.MatchService;
import com.nhomgame.web.dto.ApiResponse;

import jakarta.validation.Valid;

@RestController
@Validated
public class MatchJoinController {

    private final MatchService matchService;
    private final AuthService authService;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MatchJoinController.class);

    public MatchJoinController(MatchService matchService, AuthService authService) {
        this.matchService = matchService;
        this.authService = authService;
    }

    @PostMapping("/api/matches/join")
    public ResponseEntity<ApiResponse<Object>> joinMatch(
            @Valid @RequestBody MatchJoinRequest request,
            Principal principal) {

        // 1. Kiểm tra xác thực
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, false, "Unauthorized", null));
        }

        String email = principal.getName();
        User user = authService.findByEmail(email);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, false, "User not found", null));
        }

        String userId = user.getId();
        log.info("User {} đang yêu cầu tham gia phòng với PIN: {}", userId, request.getPinCode());

        try {
            Match match = matchService.joinMatchWithPin(userId, user.getName(), request.getPinCode());

            return ResponseEntity.ok(new ApiResponse<>(200, true, "Tham gia phòng thành công", match.getId()));

        } catch (IllegalArgumentException ex) {
            log.warn("Join match failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, false, ex.getMessage(), null));
        } catch (Exception ex) {
            log.error("Lỗi hệ thống khi joinMatch", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(500, false, "Internal server error", null));
        }
    }

    public static class MatchJoinRequest {
        @jakarta.validation.constraints.NotBlank(message = "Pin code không được để trống")
        private String pinCode;

        public MatchJoinRequest() {}

        public String getPinCode() { return pinCode; }
        public void setPinCode(String pinCode) { this.pinCode = pinCode; }
    }
}