package com.nhomgame.service.match;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.nhomgame.domain.auth.User;
import com.nhomgame.domain.match.Match;
import com.nhomgame.domain.match.Match.GameBoard;
import com.nhomgame.domain.match.Match.Player;
import com.nhomgame.domain.match.dto.MoveResult.RevealedCellResult;
import com.nhomgame.domain.match.WaitingQueue;
import com.nhomgame.domain.match.WaitingQueue.Preferences;
import com.nhomgame.domain.match.dto.CreateMatchRequest;
import com.nhomgame.domain.match.dto.MatchFindRequest;
import com.nhomgame.domain.match.dto.MatchResultResponse;
import com.nhomgame.domain.match.dto.MatchResultResponse.PlayerResultInfo;
import com.nhomgame.domain.match.MatchHistory;
import com.nhomgame.domain.match.dto.MatchHistoryDTO;
import com.nhomgame.domain.match.dto.OpponentDTO;
import com.nhomgame.infrastructure.auth.UserRepository;
import com.nhomgame.infrastructure.match.MatchHistoryRepository;
import com.nhomgame.infrastructure.match.MatchRepository;
import com.nhomgame.infrastructure.match.WaitingQueueRepository;

/**
 * Service implementation for match operations
 */
@Service
public class MatchServiceImpl implements MatchService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final MatchHistoryRepository matchHistoryRepository;
    private final com.nhomgame.service.auth.AuthService authService;
    private final Random random = new Random();
    private static final List<String> ACTIVE_MATCH_STATUSES = List.of(
            "waiting", "WAITING", "ready", "READY", "PREPARATION", "PLAYING", "playing");
    private static final long STALE_PREPARATION_TIMEOUT_MINUTES = 10;
        private static final int WINNER_ELO_DELTA = 20;
        private static final int LOSER_ELO_DELTA = -10;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MatchServiceImpl.class);

    public MatchServiceImpl(WaitingQueueRepository waitingQueueRepository,
                          MatchRepository matchRepository,
                          UserRepository userRepository,
                          MatchHistoryRepository matchHistoryRepository,
                          com.nhomgame.service.auth.AuthService authService) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
        this.matchHistoryRepository = matchHistoryRepository;
        this.authService = authService;
    }

    @Override
    public Page<MatchHistoryDTO> getMatchHistory(String userId, Pageable pageable) {
        log.info("Fetching match history for user {}", userId);
        
        java.util.List<String> finishedStatuses = java.util.Arrays.asList("finished", "FINISHED");
        Page<Match> matches = matchRepository.findByPlayersUserIdAndStatusIn(userId, finishedStatuses, pageable);
        
        return matches.map(match -> mapToMatchHistoryDTO(match, userId));
    }

    private MatchHistoryDTO mapToMatchHistoryDTO(Match match, String userId) {
        String result = "draw";
        int eloChange = 0;
        
        if (match.getWinnerId() != null) {
            if (match.getWinnerId().equals(userId)) {
                result = "win";
                eloChange = 25;
            } else {
                result = "lose";
                eloChange = -20;
            }
        }
        
        long duration = 0;
        if (match.getStartedAt() != null && match.getFinishedAt() != null) {
            duration = java.time.Duration.between(match.getStartedAt(), match.getFinishedAt()).getSeconds();
        }
        
        String opponentId = match.getPlayers().stream()
                .map(Player::getUserId)
                .filter(id -> id != null && !id.equals(userId))
                .findFirst()
                .orElse(null);
                
        OpponentDTO opponentDTO = new OpponentDTO("Unknown", "");
        if (opponentId != null) {
            User opponent = userRepository.findById(opponentId).orElse(null);
            if (opponent != null) {
                String displayName = (opponent.getName() != null && !opponent.getName().isBlank()) 
                    ? opponent.getName() : opponent.getUsername();
                opponentDTO = new OpponentDTO(displayName, opponent.getAvatarUrl());
            }
        }
        
        return MatchHistoryDTO.builder()
                .matchId(match.getId())
                .matchType(match.getMatchType())
                .result(result)
                .opponent(opponentDTO)
                .duration(duration)
                .eloChange(eloChange)
                .playedAt(match.getFinishedAt() != null ? match.getFinishedAt() : match.getCreatedAt())
                .build();
    }

    @Override
    public WaitingQueue findMatch(String userId, MatchFindRequest request) {
        String boardSize = request != null ? request.getBoardSize() : null;
        if (boardSize == null || boardSize.isBlank()) {
            boardSize = "medium";
        }
        log.info("User {} requesting to find match with boardSize: {}", userId, boardSize);

        // 1. Get user by ID
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 2. Resolve active match from DB and clear stale currentMatchId if needed
        Match activeMatch = getActiveMatchForUser(userId);
        if (activeMatch != null) {
            // Self-healing: if match is not actually playing, allow user to "leave" and start new search
            if (!"playing".equalsIgnoreCase(activeMatch.getStatus())) {
                log.info("User {} is in a non-playing match {}, auto-leaving to allow new search", userId, activeMatch.getId());
                leaveMatch(activeMatch.getId(), userId);
            } else {
                throw new IllegalArgumentException("User is already in an active match (" + activeMatch.getId() + ")");
            }
        }

        String currentMatchId = user.getCurrentMatchId();
        if (currentMatchId != null && !currentMatchId.isBlank()) {
            Match referenced = matchRepository.findById(currentMatchId).orElse(null);
            if (referenced == null || referenced.getStatus() == null
                    || !ACTIVE_MATCH_STATUSES.contains(referenced.getStatus())) {
                user.setCurrentMatchId(null);
                user.setModifiedAt(Instant.now());
                userRepository.save(user);
            }
        }

        // 3. Check if already in waiting queue
        boolean alreadyWaiting = waitingQueueRepository.existsByUserIdAndStatus(userId, "waiting");
        if (alreadyWaiting) {
            log.warn("User {} is already waiting in queue", userId);
            WaitingQueue existing = waitingQueueRepository.findByUserIdAndStatus(userId, "waiting")
                    .orElse(null);
            if (existing != null) {
                return existing; // Return existing entry instead of creating duplicate
            }
        }

        // 4. Create waiting queue entry
        Preferences preferences = new Preferences(boardSize);
        WaitingQueue queueEntry = new WaitingQueue(userId, user.getRank(), preferences);
        queueEntry.setJoinedAt(Instant.now());

        WaitingQueue saved = waitingQueueRepository.save(queueEntry);
        log.info("User {} added to waiting queue: {}", userId, saved.getId());

        return saved;
    }

    @Override
    public WaitingQueue getWaitingQueueEntry(String userId) {
        return waitingQueueRepository.findByUserIdAndStatus(userId, "waiting")
                .orElse(null);
    }

    @Override
    public void cancelWaitingQueue(String userId) {
        waitingQueueRepository.deleteByUserId(userId);
        log.info("Cancelled waiting queue entry for user {}", userId);
    }

    @Override
    public Match createMatch(String userId, CreateMatchRequest request) {
        log.info("User {} requesting to create private match", userId);

        // 1. Get user by ID
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 2. Check if user is already in an active match
        Match activeMatch = getActiveMatchForUser(userId);
        if (activeMatch != null) {
            // Self-healing for private room creation too
            if (!"playing".equalsIgnoreCase(activeMatch.getStatus())) {
                log.info("User {} is in a non-playing match {}, auto-leaving to allow room creation", userId, activeMatch.getId());
                leaveMatch(activeMatch.getId(), userId);
            } else {
                throw new IllegalArgumentException("User is already in an active match (" + activeMatch.getId() + ")");
            }
        }

        // 3. Generate unique 4-digit PIN code
        String pinCode = generateUniquePinCode();
        log.info("Generated PIN code: {} for user {}", pinCode, userId);

        // 4. Create Match object
        Match match = new Match("private", userId, pinCode);
        match.setStatus("waiting");
        Integer timeLimitReq = request.getTurnTimeLimit();
        match.setTurnTimeLimit(timeLimitReq != null ? timeLimitReq : 30);
        match.setCurrentPlayerId(userId);

        // 5. Create GameBoard configuration
        Integer widthReq = request.getBoardWidth();
        Integer heightReq = request.getBoardHeight();
        Integer mineCountReq = request.getMineCount();
        String difficultyReq = request.getDifficulty();
        
        GameBoard gameBoard = new GameBoard(
            widthReq != null ? widthReq : 10,
            heightReq != null ? heightReq : 10,
            mineCountReq != null ? mineCountReq : 20,
            difficultyReq != null ? difficultyReq : "medium",
            3,
            new java.util.ArrayList<>(),
            new java.util.ArrayList<>(),
            new java.util.ArrayList<>()
        );
        match.getGameBoard().put(userId, gameBoard);

        // 6. Add host player to players list
        Player hostPlayer = new Player(userId, user.getUsername());
        hostPlayer.setReady(true);
        hostPlayer.setHealth(3);
        hostPlayer.setShieldAvailable(true);
        match.getPlayers().add(hostPlayer);

        // 7. Save match to database
        Match savedMatch = matchRepository.save(match);
        log.info("Created match {} with PIN code {}", savedMatch.getId(), pinCode);

        // 8. Update user's currentMatchId
        user.setCurrentMatchId(savedMatch.getId());
        user.setModifiedAt(Instant.now());
        userRepository.save(user);
        log.info("Updated user {} with currentMatchId: {}", userId, savedMatch.getId());

        return savedMatch;
    }

    @Override
    public Match getMatchById(String matchId) {
        return matchRepository.findById(matchId).orElse(null);
    }

    @Override
    public Match getMatchByPin(String pinCode) {
        return matchRepository.findByPinCode(pinCode).orElse(null);
    }

    @Override
    public Match addPlayer(String matchId, String userId) {
        Match match = getMatchById(matchId);
        if (match == null) throw new IllegalArgumentException("Match not found");
        if (match.getPlayers().stream().anyMatch(p -> p.getUserId().equals(userId))) {
            return match;
        }
        Player player = new Player(userId, authService.findById(userId).getUsername());
        player.setReady(false);
        player.setHealth(3);
        player.setShieldAvailable(true);
        match.getPlayers().add(player);
        return matchRepository.save(match);
    }

    @Override
    public Match setPlayerReady(String matchId, String userId, boolean ready) {
        Match match = getMatchById(matchId);
        if (match == null) return null;
        match.getPlayers().stream()
            .filter(p -> p.getUserId().equals(userId))
            .findFirst()
            .ifPresent(p -> p.setReady(ready));
        return matchRepository.save(match);
    }

    @Override
    public Match startMatch(String matchId) {
        Match match = getMatchById(matchId);
        if (match == null) return null;
        match.setStatus("playing");
        match.setCurrentTurn(0);
        match.setTurnStartTime(Instant.now());
        match.getPlayers().forEach(p -> p.setShieldAvailable(true));
        if (!match.getPlayers().isEmpty()) {
            String firstPlayerId = match.getPlayers().get(0).getUserId();
            match.setCurrentPlayerId(firstPlayerId);
        }
        return matchRepository.save(match);
    }

    @Override
    public com.nhomgame.domain.match.dto.MoveResult applyMove(String matchId, String userId, int x, int y, String action) {
        Match match = getMatchById(matchId);
        if (match == null) return null;

        if (match.getStatus() == null || !"PLAYING".equalsIgnoreCase(match.getStatus())) {
            return null;
        }

        Player player = match.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst().orElse(null);
        if (player == null) return null;

        Player opponent = match.getPlayers().stream()
                .filter(p -> !p.getUserId().equals(userId))
                .findFirst().orElse(null);
        if (opponent == null) return null;

        Match.GameBoard targetBoard = getOrCreatePlayerBoard(match, opponent.getUserId());
        String coord = toCoordKey(x, y);
        Set<String> bombSet = new HashSet<>(targetBoard.getBombs());

        String result = "safe";
        List<RevealedCellResult> revealedCells = new ArrayList<>();
        boolean shieldBlocked = false;
        if ("flag".equalsIgnoreCase(action)) {
            toggleFlag(targetBoard, coord);
            result = "flag";
        } else {
            if (targetBoard.getRevealed().contains(coord)) {
                result = "safe";
            } else {
                if (bombSet.contains(coord)) {
                    targetBoard.getRevealed().add(coord);
                    if (player.isShieldAvailable()) {
                        shieldBlocked = true;
                        player.setShieldAvailable(false);
                        result = "shield_blocked";
                    } else {
                        result = "bomb";
                        player.setHealth(Math.max(0, player.getHealth() - 1));
                    }
                } else {
                    revealedCells = revealSafeCells(targetBoard, x, y, bombSet);
                }
            }
        }

        // Append move history for game state reconstruction
        Match.Move recordedMove = new Match.Move(userId, x, y, action);
        match.getMoves().add(recordedMove);

        match.setUpdatedAt(Instant.now());

        matchRepository.save(match);

        com.nhomgame.domain.match.dto.MoveResult mr = new com.nhomgame.domain.match.dto.MoveResult();
        mr.setUserId(userId);
        mr.setX(x);
        mr.setY(y);
        mr.setAction(action);
        mr.setResult(result);
        mr.setHealth(player.getHealth());
        mr.setShieldBlocked(shieldBlocked);
        mr.setShieldAvailable(player.isShieldAvailable());
        mr.setRevealedCells(revealedCells);

        if (hasRevealedAllSafeCells(targetBoard)) {
            mr.setGameOver(true);
            mr.setWinnerId(userId);
            finalizeMatch(match, userId, mr, "all_safe_revealed");
            return mr;
        }

        if (player.getHealth() == 0) {
            mr.setGameOver(true);
            mr.setWinnerId(match.getPlayers().stream()
                    .filter(p -> !p.getUserId().equals(userId))
                    .findFirst()
                    .map(Player::getUserId).orElse(null));
                finalizeMatch(match, mr.getWinnerId(), mr, "health_exhausted");
        }

        return mr;
    }

    private Match.GameBoard getOrCreatePlayerBoard(Match match, String playerId) {
        if (match.getGameBoard() == null) {
            match.setGameBoard(new java.util.HashMap<>());
        }

        Map<String, Match.GameBoard> boardMap = match.getGameBoard();
        Match.GameBoard board = boardMap.get(playerId);
        if (board == null) {
            board = new Match.GameBoard(10, 10, 20, "medium", 3,
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            boardMap.put(playerId, board);
        }

        if (board.getBombs() == null) {
            board.setBombs(new ArrayList<>());
        }
        if (board.getFlags() == null) {
            board.setFlags(new ArrayList<>());
        }
        if (board.getRevealed() == null) {
            board.setRevealed(new ArrayList<>());
        }
        return board;
    }

    private void toggleFlag(Match.GameBoard board, String coord) {
        if (board.getFlags().contains(coord)) {
            board.getFlags().remove(coord);
        } else {
            board.getFlags().add(coord);
        }
    }

    private List<RevealedCellResult> revealSafeCells(Match.GameBoard board, int startX, int startY, Set<String> bombSet) {
        List<RevealedCellResult> newlyRevealed = new ArrayList<>();

        int width = board.getWidth() != null ? board.getWidth() : 10;
        int height = board.getHeight() != null ? board.getHeight() : 10;

        if (!isInsideBoard(startX, startY, width, height)) {
            return newlyRevealed;
        }

        Set<String> visited = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        queue.offer(new int[] { startX, startY });

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int x = current[0];
            int y = current[1];
            String currentCoord = toCoordKey(x, y);

            if (!visited.add(currentCoord)) {
                continue;
            }

            if (!isInsideBoard(x, y, width, height)
                    || bombSet.contains(currentCoord)
                    || board.getRevealed().contains(currentCoord)
                    || board.getFlags().contains(currentCoord)) {
                continue;
            }

            int adjacentBombs = countAdjacentBombs(x, y, bombSet, width, height);
            board.getRevealed().add(currentCoord);
            newlyRevealed.add(new RevealedCellResult(x, y, adjacentBombs));

            if (adjacentBombs != 0) {
                continue;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int nx = x + dx;
                    int ny = y + dy;
                    if (isInsideBoard(nx, ny, width, height)) {
                        queue.offer(new int[] { nx, ny });
                    }
                }
            }
        }

        return newlyRevealed;
    }

    private int countAdjacentBombs(int x, int y, Set<String> bombSet, int width, int height) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = x + dx;
                int ny = y + dy;
                if (!isInsideBoard(nx, ny, width, height)) {
                    continue;
                }
                if (bombSet.contains(toCoordKey(nx, ny))) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean isInsideBoard(int x, int y, int width, int height) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private void finalizeMatch(Match match, String winnerId, com.nhomgame.domain.match.dto.MoveResult moveResult, String endReason) {
        if (match == null || "FINISHED".equals(match.getStatus())) {
            return;
        }

        String loserId = match.getPlayers().stream()
                .map(Player::getUserId)
                .filter(id -> id != null && !id.equals(winnerId))
                .findFirst()
                .orElse(null);

        match.setStatus("FINISHED");
        match.setWinnerId(winnerId);
        match.setFinishedAt(Instant.now());
        match.setUpdatedAt(Instant.now());
        matchRepository.save(match);

        applyEloDelta(winnerId, WINNER_ELO_DELTA);
        if (loserId != null) {
            applyEloDelta(loserId, LOSER_ELO_DELTA);
        }
        
        // Record match history before clearing current match
        recordMatchHistory(match, endReason);
        
        clearCurrentMatchForParticipants(match.getPlayers(), match.getId());

        if (moveResult != null) {
            moveResult.setWinnerEloDelta(WINNER_ELO_DELTA);
            moveResult.setLoserEloDelta(LOSER_ELO_DELTA);
        }
    }

    private void recordMatchHistory(Match match, String endReason) {
        try {
            long duration = 0;
            if (match.getStartedAt() != null && match.getFinishedAt() != null) {
                duration = ChronoUnit.SECONDS.between(match.getStartedAt(), match.getFinishedAt());
            }

            List<MatchHistory.PlayerHistoryStats> playerStatsList = new ArrayList<>();
            for (Match.Player player : match.getPlayers()) {
                String userId = player.getUserId();
                String result = "draw";
                if (match.getWinnerId() != null) {
                    result = match.getWinnerId().equals(userId) ? "win" : "lose";
                }

                Match.GameBoard board = getOrCreatePlayerBoard(match, userId);
                
                // Calculate stats
                // bombsPlaced: How many bombs this player placed on OPPONENT'S board.
                String opponentId = match.getPlayers().stream()
                        .map(Match.Player::getUserId)
                        .filter(id -> id != null && !id.equals(userId))
                        .findFirst().orElse(null);
                
                int bombsPlaced = 0;
                int bombsFound = 0;
                if (opponentId != null) {
                    Match.GameBoard opponentBoard = getOrCreatePlayerBoard(match, opponentId);
                    bombsPlaced = opponentBoard.getBombs() != null ? opponentBoard.getBombs().size() : 0;
                    // bombsFound: safe cells user revealed on opponent's board.
                    bombsFound = opponentBoard.getRevealed() != null ? opponentBoard.getRevealed().size() : 0;
                }

                int flagsPlaced = board.getFlags() != null ? board.getFlags().size() : 0;
                int turnsPlayed = (int) match.getMoves().stream()
                        .filter(m -> m.getPlayerId() != null && m.getPlayerId().equals(userId))
                        .count();

                MatchHistory.GameStats gameStats = MatchHistory.GameStats.builder()
                        .bombsPlaced(bombsPlaced)
                        .bombsFound(bombsFound)
                        .flagsPlaced(flagsPlaced)
                        .turnsPlayed(turnsPlayed)
                        .build();

                User user = userRepository.findById(userId).orElse(null);
                String username = user != null ? user.getUsername() : "Unknown";

                playerStatsList.add(MatchHistory.PlayerHistoryStats.builder()
                        .userId(userId)
                        .username(username)
                        .result(result)
                        .finalHealth(player.getHealth())
                        .stats(gameStats)
                        .build());
            }

            MatchHistory history = MatchHistory.builder()
                    .matchId(match.getId())
                    .players(playerStatsList)
                    .matchType("PVP")
                    .duration(duration)
                    .endReason(endReason)
                    .playedAt(match.getFinishedAt() != null ? match.getFinishedAt() : Instant.now())
                    .build();

            matchHistoryRepository.save(history);
            log.info("Saved match history for match {}", match.getId());
        } catch (Exception e) {
            log.error("Failed to save match history for match {}: {}", match.getId(), e.getMessage());
        }
    }

    private void applyEloDelta(String userId, int delta) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        int nextRank = Math.max(0, user.getRank() + delta);
        user.setRank(nextRank);
        user.setModifiedAt(Instant.now());
        userRepository.save(user);
    }

    private void clearCurrentMatchForParticipants(List<Match.Player> players, String matchId) {
        if (players == null || matchId == null || matchId.isBlank()) {
            return;
        }

        for (Match.Player participant : players) {
            if (participant == null || participant.getUserId() == null || participant.getUserId().isBlank()) {
                continue;
            }

            User user = userRepository.findById(participant.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }

            if (matchId.equals(user.getCurrentMatchId())) {
                user.setCurrentMatchId(null);
                user.setModifiedAt(Instant.now());
                userRepository.save(user);
            }
        }
    }

    private boolean hasRevealedAllSafeCells(Match.GameBoard board) {
        int width = board.getWidth() != null ? board.getWidth() : 10;
        int height = board.getHeight() != null ? board.getHeight() : 10;

        Set<String> bombs = new HashSet<>(board.getBombs());
        int safeCellCount = (width * height) - bombs.size();
        if (safeCellCount <= 0) {
            return false;
        }

        long revealedSafe = board.getRevealed().stream()
                .filter(c -> c != null && !bombs.contains(c))
                .distinct()
                .count();
        return revealedSafe >= safeCellCount;
    }

    private String toCoordKey(int x, int y) {
        return x + "," + y;
    }

    @Override
    public void switchTurn(String matchId) {
        Match match = getMatchById(matchId);
        if (match == null) return;
        if (match.getPlayers().isEmpty()) return;

        int currentTurnIdx = match.getCurrentTurn();
        int playersCount = match.getPlayers().size();
        int nextTurnIdx = (currentTurnIdx + 1) % playersCount;

        String nextPlayerId = match.getPlayers().get(nextTurnIdx).getUserId();
        match.setCurrentTurn(nextTurnIdx);
        match.setCurrentPlayerId(nextPlayerId);
        match.setTurnStartTime(Instant.now());
        matchRepository.save(match);
    }

    @Override
    public void markPlayerDisconnected(String matchId, String userId) {
        try {
            leaveMatch(matchId, userId);
        } catch (Exception ex) {
            log.warn("Error disconnecting player {} from match {}: {}", userId, matchId, ex.getMessage());
        }
    }

    @Override
    public Match leaveMatch(String matchId, String userId) {
        Match match = getMatchById(matchId);

        // If match is already deleted from DB, still clear user state
        if (match == null) {
            log.warn("leaveMatch: match {} not found, clearing currentMatchId for user {}", matchId, userId);
            var staleUser = authService.findById(userId);
            if (staleUser != null && staleUser.getCurrentMatchId() != null && staleUser.getCurrentMatchId().equals(matchId)) {
                staleUser.setCurrentMatchId(null);
                authService.saveUser(staleUser);
            }
            return null;
        }

        boolean removed = match.getPlayers().removeIf(p -> p.getUserId().equals(userId));
        if (!removed) {
            throw new IllegalArgumentException("User is not part of this match");
        }

        // Clean board for leaving player
        if (match.getGameBoard() != null) {
            match.getGameBoard().remove(userId);
        }

        // Handle host transfer / room cleanup
        if (match.getHostId() != null && match.getHostId().equals(userId)) {
            if (match.getPlayers().isEmpty()) {
                matchRepository.delete(match);
                log.info("Host {} left and match {} had no players; deleted", userId, matchId);
                // reset leaving user in AuthService
                var leavingUser = authService.findById(userId);
                if (leavingUser != null) {
                    leavingUser.setCurrentMatchId(null);
                    authService.saveUser(leavingUser);
                }
                return null;
            } else {
                Match.Player newHost = match.getPlayers().get(0);
                match.setHostId(newHost.getUserId());
                log.info("Host {} left match {}. New host {}", userId, matchId, newHost.getUserId());
            }
        }

        // If no players remain, delete match
        if (match.getPlayers().isEmpty()) {
            matchRepository.delete(match);
            log.info("Match {} emptied after {} leaving and was deleted", matchId, userId);
        } else {
            // Adjust current player if needed
            if (match.getCurrentPlayerId() != null && match.getCurrentPlayerId().equals(userId)) {
                String newCurrent = match.getPlayers().get(0).getUserId();
                match.setCurrentPlayerId(newCurrent);
                match.setCurrentTurn(0);
            }

            // If only one player remains, move to waiting status
            if (match.getPlayers().size() < 2 && "playing".equalsIgnoreCase(match.getStatus())) {
                match.setStatus("waiting");
            }

            match.setUpdatedAt(Instant.now());
            matchRepository.save(match);
        }

        // Clear user currentMatchId
        var leavingUser2 = authService.findById(userId);
        if (leavingUser2 != null) {
            leavingUser2.setCurrentMatchId(null);
            authService.saveUser(leavingUser2);
        }

        return match;
    }

    @Override
    public Match joinMatchWithPin(String userId, String username, String pinCode) {
        // 1. Tìm trận đấu có pinCode tương ứng
        Match match = matchRepository.findByPinCode(pinCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng chờ với mã PIN này."));

        // Kiểm tra status = "waiting"
        if (!"waiting".equals(match.getStatus())) {
            throw new IllegalArgumentException("Phòng này không ở trạng thái chờ");
        }

        // 2. Kiểm tra số lượng người chơi hiện tại, nếu đã đủ 2 người thì báo lỗi "Room Full"
        if (match.getPlayers() != null && match.getPlayers().size() >= 2) {
            throw new IllegalArgumentException("Room Full");
        }

        // Kiểm tra xem user có lỡ tự join lại phòng của chính mình không
        boolean alreadyInRoom = match.getPlayers().stream()
                .anyMatch(p -> p.getUserId().equals(userId));
        if (alreadyInRoom) {
            throw new IllegalArgumentException("Bạn đã ở trong phòng này rồi.");
        }

        // 3. Thêm người chơi mới vào mảng players với playerNumber: 2 và isReady: false
        Match.Player player2 = new Match.Player(userId, username);
        player2.setReady(false);
        player2.setHealth(3);
        player2.setAlive(true);
        player2.setJoinedAt(Instant.now());

        match.getPlayers().add(player2);

        // 4. Nếu đủ 2 người, tự động update match status từ "waiting" -> "ready"
        // Khi đủ 2 người, match chuyển sang trạng thái "ready" (chờ cả 2 ready để play)
        if (match.getPlayers().size() >= 2) {
            match.setStatus("ready");
            match.setUpdatedAt(Instant.now());
            log.info("Match {} now has 2 players, status changed to 'ready'", match.getId());
        }

        // Lưu bản ghi Match đã cập nhật vào DB
        Match savedMatch = matchRepository.save(match);

        // 5. Cập nhật currentMatchId cho người chơi vừa tham gia
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setCurrentMatchId(savedMatch.getId());
            userRepository.save(user);
        }

        // 6. Yêu cầu Real-time: Sẽ được handle bởi MatchWebSocketController
        // (Socket notification sẽ được gửi thông qua /topic/match.{matchId})
        log.info("User {} joined match {} successfully", userId, savedMatch.getId());

        return savedMatch;
    }

    @Override
    public Match getActiveMatchForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }

        List<Match> candidates = matchRepository.findByPlayersContainingAndStatusIn(userId, ACTIVE_MATCH_STATUSES);
        for (Match match : candidates) {
            if (isStalePreparation(match)) {
                expireStaleMatch(match);
                continue;
            }
            return match;
        }

        return null;
    }

    private boolean isStalePreparation(Match match) {
        if (match == null || match.getStatus() == null || !"PREPARATION".equalsIgnoreCase(match.getStatus())) {
            return false;
        }

        Instant referenceTime = match.getUpdatedAt() != null ? match.getUpdatedAt() : match.getCreatedAt();
        if (referenceTime == null) {
            return false;
        }

        Instant threshold = Instant.now().minus(STALE_PREPARATION_TIMEOUT_MINUTES, ChronoUnit.MINUTES);
        return referenceTime.isBefore(threshold);
    }

    private void expireStaleMatch(Match match) {
        if (match == null || match.getId() == null) {
            return;
        }

        log.info("Expiring stale PREPARATION match {}", match.getId());
        match.setStatus("cancelled");
        match.setUpdatedAt(Instant.now());
        matchRepository.save(match);

        if (match.getPlayers() == null) {
            return;
        }

        for (Match.Player player : match.getPlayers()) {
            if (player == null || player.getUserId() == null || player.getUserId().isBlank()) {
                continue;
            }

            User participant = userRepository.findById(player.getUserId()).orElse(null);
            if (participant == null) {
                continue;
            }

            if (match.getId().equals(participant.getCurrentMatchId())) {
                participant.setCurrentMatchId(null);
                participant.setModifiedAt(Instant.now());
                userRepository.save(participant);
            }
        }
    }

    /**
     * Generate a unique 4-digit PIN code (0000-9999)
     */
    private String generateUniquePinCode() {
        String pinCode;
        int attempts = 0;
        final int maxAttempts = 100;

        do {
            int pin = random.nextInt(10000); // 0-9999
            pinCode = String.format("%04d", pin);
            attempts++;

            if (attempts >= maxAttempts) {
                throw new IllegalStateException("Unable to generate unique PIN code after " + maxAttempts + " attempts");
            }
        } while (matchRepository.existsByPinCode(pinCode));

        return pinCode;
    }

    @Override
    public MatchResultResponse getMatchResult(String matchId) {
        // Try to find match from 'matches' collection first
        Match match = matchRepository.findById(matchId).orElse(null);

        // If not found or not finished, attempt to query from matchHistory
        // (In a future implementation, we would query a separate matchHistory collection)
        if (match == null || !"finished".equals(match.getStatus())) {
            throw new IllegalArgumentException("Match not found or match is not finished");
        }

        // Build player result list
        java.util.List<PlayerResultInfo> playerResults = new java.util.ArrayList<>();

        String winnerUsername = null;
        int totalMoveCount = (match.getMoves() != null) ? match.getMoves().size() : 0;

        // Get winner information
        if (match.getWinnerId() != null) {
            User winner = userRepository.findById(match.getWinnerId()).orElse(null);
            if (winner != null) {
                winnerUsername = winner.getUsername();
            }
        }

        // Process each player
        for (Player player : match.getPlayers()) {
            String userId = player.getUserId();
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) continue;

            // Calculate ELO changes using simple ELO formula
            boolean isWinner = match.getWinnerId() != null && match.getWinnerId().equals(userId);
            int rankBefore = user.getRank();
            int rankChange = calculateEloChange(rankBefore, isWinner, match.getPlayers().size());
            int rankAfter = rankBefore + rankChange;

            // Count mine hits for this player
            int mineHits = countMineHitsForPlayer(match, userId);

            PlayerResultInfo playerInfo = new PlayerResultInfo(
                userId,
                user.getUsername(),
                player.getHealth(),
                mineHits,
                player.isAlive(),
                rankBefore,
                rankAfter,
                isWinner
            );

            playerResults.add(playerInfo);
        }

        // Create and return result response
        MatchResultResponse response = new MatchResultResponse(
            matchId,
            match.getStatus(),
            match.getStartedAt(),
            match.getFinishedAt(),
            totalMoveCount,
            match.getWinnerId(),
            winnerUsername,
            playerResults
        );

        log.info("Match result retrieved for match {}: winner={}, totalMoves={}, players={}", 
                matchId, match.getWinnerId(), totalMoveCount, playerResults.size());

        return response;
    }

    /**
     * Calculate ELO change based on win/loss and players count
     * Simplified ELO calculation: +30 for win, -20 for loss (can be adjusted)
     */
    private int calculateEloChange(int currentRank, boolean won, int playersCount) {
        // Base ELO change values
        int winPoints = 30;
        int lossPoints = -20;

        // Adjust for number of players if needed
        if (playersCount > 2) {
            winPoints = (winPoints * 2) / playersCount;
            lossPoints = (lossPoints * 2) / playersCount;
        }

        return won ? winPoints : lossPoints;
    }

    /**
     * Count the number of mines hit by a specific player
     * A mine hit is counted when action="open" and result="bomb"
     */
    private int countMineHitsForPlayer(Match match, String playerId) {
        if (match.getMoves() == null) {
            return 0;
        }

        // Count moves where playerId hit a mine
        // Note: This requires tracking move results in the Move object
        // For now, we count based on health loss (3 - current health)
        Player player = match.getPlayers().stream()
                .filter(p -> p.getUserId().equals(playerId))
                .findFirst()
                .orElse(null);

        if (player == null) {
            return 0;
        }

        // Calculate mine hits as initial health (3) minus current health
        return Math.max(0, 3 - player.getHealth());
    }

}
