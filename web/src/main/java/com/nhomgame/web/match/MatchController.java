package com.nhomgame.web.match;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.nhomgame.domain.auth.User;
import com.nhomgame.domain.match.Match;
import com.nhomgame.domain.match.WaitingQueue;
import com.nhomgame.domain.match.dto.CreateMatchRequest;
import com.nhomgame.domain.match.dto.MatchFindRequest;
import com.nhomgame.domain.match.dto.WsEvent;
import com.nhomgame.infrastructure.auth.UserRepository;
import com.nhomgame.infrastructure.match.MatchRepository;
import com.nhomgame.infrastructure.match.WaitingQueueRepository;
import com.nhomgame.service.auth.AuthService;
import com.nhomgame.service.match.MatchService;
import com.nhomgame.service.match.MatchmakingService;
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
    private final MatchmakingService matchmakingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WaitingQueueRepository waitingQueueRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;

    private static final java.util.Set<String> ACTIVE_MATCH_STATUSES = java.util.Set.of("waiting", "PREPARATION", "playing");
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MatchController.class);

    public MatchController(MatchService matchService, AuthService authService, MatchmakingService matchmakingService,
                           SimpMessagingTemplate messagingTemplate, WaitingQueueRepository waitingQueueRepository,
                           MatchRepository matchRepository, UserRepository userRepository) {
        this.matchService = matchService;
        this.authService = authService;
        this.matchmakingService = matchmakingService;
        this.messagingTemplate = messagingTemplate;
        this.waitingQueueRepository = waitingQueueRepository;
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
    }

    /**
     * POST /api/match/find
     * 
     * Add user to waiting queue to find a match
     * 
     * Request body: {} (empty - board is fixed at 10x10 with 20 mines)
     * 
     * Authentication: Required (JWT Bearer token)
     * 
     * Response: ApiResponse<WaitingQueueResponse>
     */
    @PostMapping("/api/match/find")
    public ResponseEntity<ApiResponse<WaitingQueueResponse>> findMatch(
            @RequestBody(required = false) MatchFindRequest request,
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

            // Pairing fallback in web layer (important when running web module directly)
            pairWaitingUsersIfPossible();

            // If user has been paired immediately, return matchId to let FE redirect instantly
            Match activeMatch = resolveActiveMatchByUserRecord(userId);

            if (activeMatch != null && activeMatch.getId() != null && activeMatch.getPlayers() != null) {
                log.info("Match found for user {}. Broadcasting to {} players", userId, activeMatch.getPlayers().size());
                for (Match.Player p : activeMatch.getPlayers()) {
                    if (p == null || p.getUserId() == null || p.getUserId().isBlank()) {
                        log.warn("Skipping null or blank userId player");
                        continue;
                    }
                    log.info("Sending WebSocket event to user {} on topic /topic/user.{}.matchmaking", p.getUserId(), p.getUserId());
                    messagingTemplate.convertAndSend(
                            "/topic/user." + p.getUserId() + ".matchmaking",
                            new WsEvent<>("match_found", new MatchFoundPayload(activeMatch.getId(), activeMatch.getStatus())));
                    log.info("WebSocket event sent successfully to user {}", p.getUserId());
                }
            } else {
                log.info("No active match found for user {} yet", userId);
            }

            // Map to response DTO
            WaitingQueueResponse response = new WaitingQueueResponse(queueEntry, activeMatch);

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

    private synchronized void pairWaitingUsersIfPossible() {
        List<WaitingQueue> waitingUsers = waitingQueueRepository.findAll().stream()
                .filter(q -> q != null && q.getStatus() != null && "waiting".equalsIgnoreCase(q.getStatus()))
                .sorted(Comparator.comparing(q -> q.getJoinedAt() != null ? q.getJoinedAt() : q.getCreatedAt()))
                .toList();

        if (waitingUsers.size() < 2) {
            return;
        }

        User firstUser = null;
        User secondUser = null;
        WaitingQueue firstQueue = null;
        WaitingQueue secondQueue = null;

        for (WaitingQueue queue : waitingUsers) {
            User candidate = resolveEligibleUserForPairing(queue);
            if (candidate == null) {
                continue;
            }

            if (firstUser == null) {
                firstUser = candidate;
                firstQueue = queue;
                continue;
            }

            if (firstUser.getId().equals(candidate.getId())) {
                continue;
            }

            secondUser = candidate;
            secondQueue = queue;
            break;
        }

        if (firstUser == null || secondUser == null || firstQueue == null || secondQueue == null) {
            return;
        }

        Match match = new Match("public", firstUser.getId(), null);
        match.setStatus("PREPARATION");
        match.setCurrentPlayerId(firstUser.getId());
        match.setTurnTimeLimit(30);
        match.setCurrentTurn(0);
        match.setCreatedAt(java.time.Instant.now());
        match.setUpdatedAt(java.time.Instant.now());

        Match.Player player1 = new Match.Player(firstUser.getId(), firstUser.getUsername());
        player1.setReady(false);
        player1.setHealth(3);

        Match.Player player2 = new Match.Player(secondUser.getId(), secondUser.getUsername());
        player2.setReady(false);
        player2.setHealth(3);

        match.getPlayers().add(player1);
        match.getPlayers().add(player2);

        Match.GameBoard board1 = new Match.GameBoard(10, 10, 20, "medium", 3,
                new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Match.GameBoard board2 = new Match.GameBoard(10, 10, 20, "medium", 3,
                new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>());

        match.getGameBoard().put(firstUser.getId(), board1);
        match.getGameBoard().put(secondUser.getId(), board2);

        Match createdMatch = matchRepository.save(match);

        firstUser.setCurrentMatchId(createdMatch.getId());
        firstUser.setModifiedAt(java.time.Instant.now());
        userRepository.save(firstUser);

        secondUser.setCurrentMatchId(createdMatch.getId());
        secondUser.setModifiedAt(java.time.Instant.now());
        userRepository.save(secondUser);

        waitingQueueRepository.deleteById(firstQueue.getId());
        waitingQueueRepository.deleteById(secondQueue.getId());

        log.info("[WEB PAIRING] Matched users {} and {} into match {}", firstUser.getId(), secondUser.getId(), createdMatch.getId());
    }

    private User resolveEligibleUserForPairing(WaitingQueue queue) {
        if (queue == null || queue.getUserId() == null || queue.getUserId().isBlank()) {
            if (queue != null && queue.getId() != null) {
                waitingQueueRepository.deleteById(queue.getId());
            }
            return null;
        }

        User user = userRepository.findById(queue.getUserId()).orElse(null);
        if (user == null) {
            waitingQueueRepository.deleteById(queue.getId());
            return null;
        }

        String currentMatchId = user.getCurrentMatchId();
        if (currentMatchId == null || currentMatchId.isBlank()) {
            return user;
        }

        Match existing = matchRepository.findById(currentMatchId).orElse(null);
        if (existing != null && ACTIVE_MATCH_STATUSES.contains(existing.getStatus())) {
            waitingQueueRepository.deleteById(queue.getId());
            return null;
        }

        user.setCurrentMatchId(null);
        user.setModifiedAt(java.time.Instant.now());
        userRepository.save(user);
        return user;
    }

    private Match resolveActiveMatchByUserRecord(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getCurrentMatchId() != null && !user.getCurrentMatchId().isBlank()) {
            Match direct = matchRepository.findById(user.getCurrentMatchId()).orElse(null);
            if (direct != null && ACTIVE_MATCH_STATUSES.contains(direct.getStatus())) {
                return direct;
            }
        }
        return matchService.getActiveMatchForUser(userId);
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
     * POST /api/matches/join
     *
     * Join a private room by pin code
     * Request body: { "pinCode": "4198" }
     */
    @PostMapping("/api/matches/join")
    public ResponseEntity<ApiResponse<CreateMatchResponse>> joinMatchByPin(
            @RequestBody JoinMatchByPinRequest request,
            Principal principal) {

        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(401, false, "Unauthorized", null));
        }

        if (request == null || request.getPinCode() == null || request.getPinCode().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, false, "Pin code is required", null));
        }

        String email = principal.getName();
        User user = authService.findByEmail(email);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(404, false, "User not found", null));
        }

        String userId = user.getId();

        try {
            Match match = matchService.getMatchByPin(request.getPinCode());
            if (match == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>(404, false, "Match not found", null));
            }

            if (user.getCurrentMatchId() != null && !user.getCurrentMatchId().isEmpty() && !user.getCurrentMatchId().equals(match.getId())) {
                // If user has stale currentMatchId, clear it and continue.
                Match activeMatch = matchService.getMatchById(user.getCurrentMatchId());
                if (activeMatch == null || !("waiting".equalsIgnoreCase(activeMatch.getStatus()) || "playing".equalsIgnoreCase(activeMatch.getStatus()))) {
                    user.setCurrentMatchId(null);
                    user.setModifiedAt(java.time.Instant.now());
                    authService.saveUser(user);
                } else {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new ApiResponse<>(400, false, "User is already in an active match", null));
                }
            }

            if (match.getPlayers().stream().anyMatch(p -> p.getUserId().equals(userId))) {
                // already in room
            } else if (match.getPlayers().size() >= 2) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(400, false, "Match is full", null));
            } else {
                matchService.addPlayer(match.getId(), userId);
            }

            // update user currentMatchId for join success (or rejoin existing room)
            user.setCurrentMatchId(match.getId());
            user.setModifiedAt(java.time.Instant.now());
            authService.saveUser(user);

            CreateMatchResponse response = new CreateMatchResponse(matchService.getMatchById(match.getId()));
            return ResponseEntity.ok(new ApiResponse<>(200, true, "Joined match successfully", response));

        } catch (IllegalArgumentException ex) {
            log.warn("Invalid join match request: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, false, ex.getMessage(), null));
        } catch (Exception ex) {
            log.error("Unexpected error in joinMatchByPin", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(500, false, "Internal server error", null));
        }
    }

    /**
     * POST /api/match/join
     *
     * Join the matchmaking queue for finding an opponent
     *
     * Request body: { "userId": "string" }
     *
     * Response: ApiResponse with Match object if opponent found, or message if waiting
     */
    @PostMapping("/api/match/join")
    public ResponseEntity<ApiResponse<?>> joinQueue(@Valid @RequestBody JoinQueueRequest request) {

        if (request == null || request.getUserId() == null || request.getUserId().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, false, "UserId is required", null));
        }

        String userId = request.getUserId();
        log.info("Join queue request from user {}", userId);

        try {
            // Call matchmaking service to join queue
            Match match = matchmakingService.joinQueue(userId);

            if (match == null) {
                // No opponent found, user added to waiting queue
                return ResponseEntity.ok(new ApiResponse<>(200, true, "Waiting for opponent", null));
            } else {
                // Opponent found, match created
                return ResponseEntity.ok(new ApiResponse<>(200, true, "Match found", match));
            }

        } catch (IllegalStateException ex) {
            log.warn("User already in active match: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, false, ex.getMessage(), null));
        } catch (Exception ex) {
            log.error("Unexpected error in joinQueue", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(500, false, "Internal server error", null));
        }
    }

    /**
     * Request DTO for joining the matchmaking queue
     */
    public static class JoinQueueRequest {
        private String userId;

        public JoinQueueRequest() {
        }

        public JoinQueueRequest(String userId) {
            this.userId = userId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
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

        // Authorization: only match members can query match state
        boolean isMember = match.getPlayers().stream()
                .anyMatch(p -> p.getUserId().equals(currentUser.getId()));
        if (!isMember && !match.getHostId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(403, false, "Forbidden: user not in this match", null));
        }

        try {
            List<PlayerState> players = new ArrayList<>();
            int idx = 0;
            for (Match.Player player : match.getPlayers()) {
                idx++;
                User u = authService.findById(player.getUserId());
                String displayName = (u != null && u.getName() != null && !u.getName().isBlank()) ? u.getName() : player.getUsername();
                String avatar = (u != null && u.getAvatarUrl() != null) ? u.getAvatarUrl() : "";
                int rank = (u != null) ? u.getRank() : 0;

                PlayerState playerState = new PlayerState();
                playerState.setUserId(player.getUserId());
                playerState.setDisplayName(displayName);
                playerState.setAvatar(avatar);
                playerState.setRank(rank);
                playerState.setHealth(player.getHealth());
                playerState.setIsReady(player.isReady());
                playerState.setPlayerNumber(idx);
                players.add(playerState);
            }

            String player1Id = match.getPlayers().size() > 0 ? match.getPlayers().get(0).getUserId() : null;
            String player2Id = match.getPlayers().size() > 1 ? match.getPlayers().get(1).getUserId() : null;

            List<Coordinate> player1Revealed = new ArrayList<>();
            List<Coordinate> player2Revealed = new ArrayList<>();
            List<Coordinate> player1Flags = new ArrayList<>();
            List<Coordinate> player2Flags = new ArrayList<>();

            if (match.getMoves() != null) {
                for (Match.Move move : match.getMoves()) {
                    if (move == null || move.getAction() == null) {
                        continue;
                    }
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
                    match.getId(),
                    match.getPinCode(),
                    match.getStatus(),
                    match.getGameBoard(),
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
     * DELETE /api/matches/{id}/leave
     *
     * User leaves a match explicitly.
     */
    @DeleteMapping("/api/matches/{id}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveMatch(
            @PathVariable("id") String matchId,
            Principal principal) {

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
        log.info("Leave match request from user {} ({}) on match {}", userId, email, matchId);

        try {
            Match result = matchService.leaveMatch(matchId, userId);

            // Notify remaining players via websocket
            messagingTemplate.convertAndSend("/topic/match." + matchId,
                    new WsEvent<>("player_left", userId));

            if (result == null) {
                return ResponseEntity.ok(new ApiResponse<>(200, true, "Left match and match deleted", null));
            }
            return ResponseEntity.ok(new ApiResponse<>(200, true, "Left match successfully", null));

        } catch (IllegalArgumentException ex) {
            log.warn("Invalid leave match request: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, false, ex.getMessage(), null));
        } catch (Exception ex) {
            log.error("Unexpected error in leaveMatch", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(500, false, "Internal server error", null));
        }
    }

    /**
     * DELETE /api/match/cancel
     *
     * Cancel a previously started match finding operation (waiting queue entry)
     */
    @DeleteMapping("/api/match/cancel")
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
        private boolean matched;
        private String matchId;
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
            this.matched = false;
            this.matchId = null;
        }

        public WaitingQueueResponse(WaitingQueue queueEntry, Match activeMatch) {
            this(queueEntry);
            if (activeMatch != null && activeMatch.getId() != null) {
                this.matched = true;
                this.matchId = activeMatch.getId();
            }
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

        public boolean isMatched() {
            return matched;
        }

        public void setMatched(boolean matched) {
            this.matched = matched;
        }

        public String getMatchId() {
            return matchId;
        }

        public void setMatchId(String matchId) {
            this.matchId = matchId;
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

    public static class ActiveMatchResponse {
        private String matchId;
        private String status;
        private String currentPlayerId;
        private Integer playerCount;

        public ActiveMatchResponse() {
        }

        public ActiveMatchResponse(Match match) {
            this.matchId = match.getId();
            this.status = match.getStatus();
            this.currentPlayerId = match.getCurrentPlayerId();
            this.playerCount = match.getPlayers() != null ? match.getPlayers().size() : 0;
        }

        public String getMatchId() {
            return matchId;
        }

        public void setMatchId(String matchId) {
            this.matchId = matchId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getCurrentPlayerId() {
            return currentPlayerId;
        }

        public void setCurrentPlayerId(String currentPlayerId) {
            this.currentPlayerId = currentPlayerId;
        }

        public Integer getPlayerCount() {
            return playerCount;
        }

        public void setPlayerCount(Integer playerCount) {
            this.playerCount = playerCount;
        }
    }

    public static class MatchFoundPayload {
        private String matchId;
        private String status;

        public MatchFoundPayload() {
        }

        public MatchFoundPayload(String matchId, String status) {
            this.matchId = matchId;
            this.status = status;
        }

        public String getMatchId() {
            return matchId;
        }

        public void setMatchId(String matchId) {
            this.matchId = matchId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
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
            if (match.getGameBoard() != null && !match.getGameBoard().isEmpty()) {
                Match.GameBoard board = match.getGameBoard().get(match.getHostId());
                if (board == null) {
                    board = match.getGameBoard().values().iterator().next();
                }
                if (board != null) {
                    this.boardWidth = board.getWidth();
                    this.boardHeight = board.getHeight();
                    this.mineCount = board.getMineCount();
                }
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

    public static class JoinMatchByPinRequest {
        private String pinCode;

        public JoinMatchByPinRequest() {
        }

        public String getPinCode() {
            return pinCode;
        }

        public void setPinCode(String pinCode) {
            this.pinCode = pinCode;
        }
    }

    public static class MatchStateResponse {
        private String matchId;
        private String pinCode;
        private String status;
        private java.util.Map<String, Match.GameBoard> gameBoard;
        private List<PlayerState> players;
        private BoardState boardState;
        private int currentTurn;
        private java.time.Instant turnStartTime;
        private int turnTimeLimit;

        public MatchStateResponse() {
        }

        public MatchStateResponse(String matchId, String pinCode, String status, java.util.Map<String, Match.GameBoard> gameBoard,
                                  List<PlayerState> players, BoardState boardState, int currentTurn,
                                  java.time.Instant turnStartTime, int turnTimeLimit) {
            this.matchId = matchId;
            this.pinCode = pinCode;
            this.status = status;
            this.gameBoard = gameBoard;
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

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public java.util.Map<String, Match.GameBoard> getGameBoard() {
            return gameBoard;
        }

        public void setGameBoard(java.util.Map<String, Match.GameBoard> gameBoard) {
            this.gameBoard = gameBoard;
        }
    }

    public static class PlayerState {
        private String userId;
        private String displayName;
        private String avatar;
        private int rank;
        private boolean isReady;
        private int playerNumber;
        private int health;

        public PlayerState() {
        }

        public PlayerState(String userId, String displayName, String avatar, int rank, boolean isReady, int playerNumber, int health) {
            this.userId = userId;
            this.displayName = displayName;
            this.avatar = avatar;
            this.rank = rank;
            this.isReady = isReady;
            this.playerNumber = playerNumber;
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

        public String getAvatar() {
            return avatar;
        }

        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }

        public boolean isReady() {
            return isReady;
        }

        public void setIsReady(boolean isReady) {
            this.isReady = isReady;
        }

        public int getPlayerNumber() {
            return playerNumber;
        }

        public void setPlayerNumber(int playerNumber) {
            this.playerNumber = playerNumber;
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

    /**
     * GET /api/matches/{id}/result
     *
     * Retrieve match result details after match is finished
     * 
     * Returns: MatchResultResponse with:
     * - Match duration in seconds
     * - Total number of moves
     * - Player information (health, mines hit, rank changes)
     * - Winner information
     */
    @org.springframework.web.bind.annotation.GetMapping("/api/matches/{id}/result")
    public ResponseEntity<ApiResponse<com.nhomgame.domain.match.dto.MatchResultResponse>> getMatchResult(
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

        try {
            com.nhomgame.domain.match.dto.MatchResultResponse result = matchService.getMatchResult(matchId);
            
            log.info("Match result retrieved for match {} by user {}", matchId, currentUser.getId());
            
            return ResponseEntity.ok(new ApiResponse<>(200, true, "Match result retrieved successfully", result));

        } catch (IllegalArgumentException ex) {
            log.warn("Invalid match result request: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(400, false, ex.getMessage(), null));
        } catch (Exception ex) {
            log.error("Unexpected error in getMatchResult", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(500, false, "Internal server error", null));
        }
    }
}

