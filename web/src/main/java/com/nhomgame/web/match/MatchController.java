package com.nhomgame.web.match;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

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
     * GET /api/matches/{id}
     *
     * Retrieve match state (board, players, current turn, timer)
     */
    @org.springframework.web.bind.annotation.GetMapping("/api/matches/{id}")
    public ResponseEntity<ApiResponse<MatchStateResponse>> getMatchState(
            @org.springframework.web.bind.annotation.PathVariable("id") String matchId,
            Principal principal) {

        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, false, "Unauthorized", null));
        }

        String email = principal.getName();
        User currentUser = authService.findByEmail(email);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, false, "User not found", null));
        }

        Match match = matchService.getMatchById(matchId);
        if (match == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, false, "Match not found", null));
        }

        try {
            List<PlayerState> players = new ArrayList<>();
            for (Match.Player player : match.getPlayers()) {
                User u = authService.findById(player.getUserId());
                String displayName = u != null && u.getName() != null && !u.getName().isBlank() ? u.getName() : player.getUsername();
                int rank = u != null ? u.getRank() : 0;
                players.add(new PlayerState(player.getUserId(), displayName, rank, player.getHealth()));
            }

            String player1Id = match.getPlayers().size() > 0 ? match.getPlayers().get(0).getUserId() : null;
            String player2Id = match.getPlayers().size() > 1 ? match.getPlayers().get(1).getUserId() : null;

            List<Coordinate> player1Revealed = new ArrayList<>();
            List<Coordinate> player2Revealed = new ArrayList<>();
            List<Coordinate> player1Flags = new ArrayList<>();
            List<Coordinate> player2Flags = new ArrayList<>();

            if (match.getMoves() != null) {
                for (Match.Move move : match.getMoves()) {
                    if (move == null || move.getAction() == null) continue;
                    String action = move.getAction();
                    Coordinate coord = new Coordinate(move.getX(), move.getY());

                    if ("open".equalsIgnoreCase(action)) {
                        if (player1Id != null && player1Id.equals(move.getPlayerId())) {
                            player1Revealed.add(coord);
                        } else if (player2Id != null && player2Id.equals(move.getPlayerId())) {
                            player2Revealed.add(coord);
                        }
                    } else if ("flag".equalsIgnoreCase(action)) {
                        if (player1Id != null && player1Id.equals(move.getPlayerId())) {
                            player1Flags.add(coord);
                        } else if (player2Id != null && player2Id.equals(move.getPlayerId())) {
                            player2Flags.add(coord);
                        }
                    }
                }
            }

            BoardState boardState = new BoardState(player1Revealed, player2Revealed, player1Flags, player2Flags);

            MatchStateResponse response = new MatchStateResponse(
                    players,
                    boardState,
                    match.getCurrentTurn(),
                    match.getTurnStartTime(),
                    match.getTurnTimeLimit());

            return ResponseEntity.ok(new ApiResponse<>(200, true, "Match state fetched", response));

        } catch (Exception ex) {
            log.error("Unexpected error in getMatchState", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(500, false, "Internal server error", null));
        }
    }

    /**
     * DELETE /api/match/cancel
     *
     * Cancel a previously started match finding operation (waiting queue entry)
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/api/match/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelMatch(Principal principal) {
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
        log.info("Cancel match find request from user {} ({})", userId, email);

        try {
            matchService.cancelWaitingQueue(userId);
            return ResponseEntity.ok(new ApiResponse<>(200, true, "Search cancelled", null));

        } catch (IllegalArgumentException ex) {
            log.warn("Invalid cancel request: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, false, ex.getMessage(), null));
        } catch (Exception ex) {
            log.error("Unexpected error in cancelMatch", ex);
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

    public static class MatchStateResponse {
        private List<PlayerState> players;
        private BoardState boardState;
        private int currentTurn;
        private java.time.Instant turnStartTime;
        private int turnTimeLimit;

        public MatchStateResponse() {
        }

        public MatchStateResponse(List<PlayerState> players, BoardState boardState, int currentTurn, java.time.Instant turnStartTime, int turnTimeLimit) {
            this.players = players;
            this.boardState = boardState;
            this.currentTurn = currentTurn;
            this.turnStartTime = turnStartTime;
            this.turnTimeLimit = turnTimeLimit;
        }

        public List<PlayerState> getPlayers() {
            return players;
        }

        public void setPlayers(List<PlayerState> players) {
            this.players = players;
        }

        public BoardState getBoardState() {
            return boardState;
        }

        public void setBoardState(BoardState boardState) {
            this.boardState = boardState;
        }

        public int getCurrentTurn() {
            return currentTurn;
        }

        public void setCurrentTurn(int currentTurn) {
            this.currentTurn = currentTurn;
        }

        public java.time.Instant getTurnStartTime() {
            return turnStartTime;
        }

        public void setTurnStartTime(java.time.Instant turnStartTime) {
            this.turnStartTime = turnStartTime;
        }

        public int getTurnTimeLimit() {
            return turnTimeLimit;
        }

        public void setTurnTimeLimit(int turnTimeLimit) {
            this.turnTimeLimit = turnTimeLimit;
        }
    }

    public static class PlayerState {
        private String userId;
        private String displayName;
        private int rank;
        private int health;

        public PlayerState() {
        }

        public PlayerState(String userId, String displayName, int rank, int health) {
            this.userId = userId;
            this.displayName = displayName;
            this.rank = rank;
            this.health = health;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public int getRank() {
            return rank;
        }

        public void setRank(int rank) {
            this.rank = rank;
        }

        public int getHealth() {
            return health;
        }

        public void setHealth(int health) {
            this.health = health;
        }
    }

    public static class BoardState {
        private List<Coordinate> player1Revealed;
        private List<Coordinate> player2Revealed;
        private List<Coordinate> player1Flags;
        private List<Coordinate> player2Flags;

        public BoardState() {
        }

        public BoardState(List<Coordinate> player1Revealed, List<Coordinate> player2Revealed, List<Coordinate> player1Flags, List<Coordinate> player2Flags) {
            this.player1Revealed = player1Revealed;
            this.player2Revealed = player2Revealed;
            this.player1Flags = player1Flags;
            this.player2Flags = player2Flags;
        }

        public List<Coordinate> getPlayer1Revealed() {
            return player1Revealed;
        }

        public void setPlayer1Revealed(List<Coordinate> player1Revealed) {
            this.player1Revealed = player1Revealed;
        }

        public List<Coordinate> getPlayer2Revealed() {
            return player2Revealed;
        }

        public void setPlayer2Revealed(List<Coordinate> player2Revealed) {
            this.player2Revealed = player2Revealed;
        }

        public List<Coordinate> getPlayer1Flags() {
            return player1Flags;
        }

        public void setPlayer1Flags(List<Coordinate> player1Flags) {
            this.player1Flags = player1Flags;
        }

        public List<Coordinate> getPlayer2Flags() {
            return player2Flags;
        }

        public void setPlayer2Flags(List<Coordinate> player2Flags) {
            this.player2Flags = player2Flags;
        }
    }

    public static class Coordinate {
        private int x;
        private int y;

        public Coordinate() {
        }

        public Coordinate(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }
    }
}

