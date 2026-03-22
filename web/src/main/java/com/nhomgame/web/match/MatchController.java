package com.nhomgame.web.match;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nhomgame.domain.match.WaitingQueue;
import com.nhomgame.domain.match.dto.MatchFindRequest;
import com.nhomgame.domain.auth.User;
import com.nhomgame.service.auth.AuthService;
import com.nhomgame.service.match.MatchService;
import com.nhomgame.web.dto.ApiResponse;

import jakarta.validation.Valid;

/**
 * REST Controller for match operations
 * Endpoint: /api/match
 */
@RestController
@RequestMapping("/api/match")
@Validated
public class MatchController {

    private final MatchService matchService;
    private final AuthService authService;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MatchController.class);

    public MatchController(MatchService matchService, AuthService authService) {
        this.matchService = matchService;
        this.authService = authService;
    }

    /**
     * POST /api/match/find
     * 
     * Add user to waiting queue to find a match
     * 
     * Request body: { "boardSize": "medium" }
     * 
     * Authentication: Required (JWT Bearer token)
     * 
     * Response: ApiResponse<WaitingQueueResponse>
     */
    @PostMapping("/find")
    public ResponseEntity<ApiResponse<WaitingQueueResponse>> findMatch(
            @Valid @RequestBody MatchFindRequest request,
            Principal principal) {

        // Authenticate user from JWT token
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
        log.info("Match find request from user {} ({})", userId, email);

        try {
            // Call service to add user to waiting queue
            WaitingQueue queueEntry = matchService.findMatch(userId, request);

            // Map to response DTO
            WaitingQueueResponse response = new WaitingQueueResponse(queueEntry);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(201, true, "Added to waiting queue", response));

        } catch (IllegalArgumentException ex) {
            log.warn("Invalid match find request: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, false, ex.getMessage(), null));
        } catch (Exception ex) {
            log.error("Unexpected error in findMatch", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(500, false, "Internal server error", null));
        }
    }

    /**
     * Response DTO for waiting queue entry
     */
    public static class WaitingQueueResponse {
        private String id;
        private String userId;
        private int rank;
        private String status;
        private String boardSize;
        private java.time.Instant joinedAt;
        private java.time.Instant createdAt;

        public WaitingQueueResponse() {
        }

        public WaitingQueueResponse(WaitingQueue queueEntry) {
            this.id = queueEntry.getId();
            this.userId = queueEntry.getUserId();
            this.rank = queueEntry.getRank();
            this.status = queueEntry.getStatus();
            this.boardSize = queueEntry.getPreferences() != null
                    ? queueEntry.getPreferences().getBoardSize()
                    : null;
            this.joinedAt = queueEntry.getJoinedAt();
            this.createdAt = queueEntry.getCreatedAt();
        }

        // Getters
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

        public int getRank() {
            return rank;
        }

        public void setRank(int rank) {
            this.rank = rank;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getBoardSize() {
            return boardSize;
        }

        public void setBoardSize(String boardSize) {
            this.boardSize = boardSize;
        }

        public java.time.Instant getJoinedAt() {
            return joinedAt;
        }

        public void setJoinedAt(java.time.Instant joinedAt) {
            this.joinedAt = joinedAt;
        }

        public java.time.Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(java.time.Instant createdAt) {
            this.createdAt = createdAt;
        }
    }
}
