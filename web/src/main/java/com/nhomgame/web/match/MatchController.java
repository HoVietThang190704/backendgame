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
import com.nhomgame.domain.match.WaitingQueue;
import com.nhomgame.domain.match.dto.CreateMatchRequest;
import com.nhomgame.domain.match.dto.MatchFindRequest;
import com.nhomgame.service.auth.AuthService;
import com.nhomgame.service.match.MatchService;
import com.nhomgame.web.dto.ApiResponse;

import jakarta.validation.Valid;

/**
 * REST Controller for match operations
 * Endpoints: /api/match/*, /api/matches/*
 */
@RestController
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
    @PostMapping("/api/match/find")
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
     * POST /api/matches/create
     * 
     * Create a new private match/room
     * 
     * Request body: {
     *   "matchType": "private",
     *   "boardWidth": 10,
     *   "boardHeight": 10,
     *   "mineCount": 20,
     *   "difficulty": "medium",
     *   "turnTimeLimit": 30
     * }
     * (All fields are optional, uses defaults if not provided)
     * 
     * Authentication: Required (JWT Bearer token)
     * 
     * Response: ApiResponse<CreateMatchResponse> with matchId and pinCode
     */
    @PostMapping("/api/matches/create")
    public ResponseEntity<ApiResponse<CreateMatchResponse>> createMatch(
            @RequestBody(required = false) CreateMatchRequest request,
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
        
        // Use default request if none provided
        if (request == null) {
            request = new CreateMatchRequest();
        }

        log.info("Create match request from user {} ({})", userId, email);

        try {
            // Call service to create match
            Match match = matchService.createMatch(userId, request);

            // Map to response DTO
            CreateMatchResponse response = new CreateMatchResponse(match);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(201, true, "Private room created successfully", response));

        } catch (IllegalArgumentException ex) {
            log.warn("Invalid create match request: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, false, ex.getMessage(), null));
        } catch (Exception ex) {
            log.error("Unexpected error in createMatch", ex);
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

    /**
     * Response DTO for create match
     */
    public static class CreateMatchResponse {
        private String matchId;
        private String pinCode;
        private String matchType;
        private String status;
        private String hostId;
        private int playerCount;
        private Integer boardWidth;
        private Integer boardHeight;
        private Integer mineCount;
        private Integer turnTimeLimit;
        private java.time.Instant createdAt;

        public CreateMatchResponse() {
        }

        public CreateMatchResponse(Match match) {
            this.matchId = match.getId();
            this.pinCode = match.getPinCode();
            this.matchType = match.getMatchType();
            this.status = match.getStatus();
            this.hostId = match.getHostId();
            this.playerCount = match.getPlayers() != null ? match.getPlayers().size() : 0;
            if (match.getGameBoard() != null) {
                this.boardWidth = match.getGameBoard().getWidth();
                this.boardHeight = match.getGameBoard().getHeight();
                this.mineCount = match.getGameBoard().getMineCount();
            }
            this.turnTimeLimit = match.getTurnTimeLimit();
            this.createdAt = match.getCreatedAt();
        }

        // Getters and Setters
        public String getMatchId() {
            return matchId;
        }

        public void setMatchId(String matchId) {
            this.matchId = matchId;
        }

        public String getPinCode() {
            return pinCode;
        }

        public void setPinCode(String pinCode) {
            this.pinCode = pinCode;
        }

        public String getMatchType() {
            return matchType;
        }

        public void setMatchType(String matchType) {
            this.matchType = matchType;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getHostId() {
            return hostId;
        }

        public void setHostId(String hostId) {
            this.hostId = hostId;
        }

        public int getPlayerCount() {
            return playerCount;
        }

        public void setPlayerCount(int playerCount) {
            this.playerCount = playerCount;
        }

        public Integer getBoardWidth() {
            return boardWidth;
        }

        public void setBoardWidth(Integer boardWidth) {
            this.boardWidth = boardWidth;
        }

        public Integer getBoardHeight() {
            return boardHeight;
        }

        public void setBoardHeight(Integer boardHeight) {
            this.boardHeight = boardHeight;
        }

        public Integer getMineCount() {
            return mineCount;
        }

        public void setMineCount(Integer mineCount) {
            this.mineCount = mineCount;
        }

        public Integer getTurnTimeLimit() {
            return turnTimeLimit;
        }

        public void setTurnTimeLimit(Integer turnTimeLimit) {
            this.turnTimeLimit = turnTimeLimit;
        }

        public java.time.Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(java.time.Instant createdAt) {
            this.createdAt = createdAt;
        }
    }
}
