package com.nhomgame.service.match;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.nhomgame.domain.auth.User;
import com.nhomgame.domain.match.Match;
import com.nhomgame.domain.match.Match.GameBoard;
import com.nhomgame.domain.match.Match.Player;
import com.nhomgame.domain.match.WaitingQueue;
import com.nhomgame.domain.match.WaitingQueue.Preferences;
import com.nhomgame.domain.match.dto.CreateMatchRequest;
import com.nhomgame.domain.match.dto.MatchFindRequest;
import com.nhomgame.infrastructure.auth.UserRepository;
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
    private final com.nhomgame.service.auth.AuthService authService;
    private final Random random = new Random();
    private static final List<String> ACTIVE_MATCH_STATUSES = List.of(
            "waiting", "WAITING", "PREPARATION", "PLAYING", "playing");
    private static final long STALE_PREPARATION_TIMEOUT_MINUTES = 10;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MatchServiceImpl.class);

    public MatchServiceImpl(WaitingQueueRepository waitingQueueRepository,
                          MatchRepository matchRepository,
                          UserRepository userRepository,
                          com.nhomgame.service.auth.AuthService authService) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
        this.authService = authService;
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
            throw new IllegalArgumentException("User is already in an active match");
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
        if (getActiveMatchForUser(userId) != null) {
            throw new IllegalArgumentException("User is already in an active match");
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

        String result = "safe";
        if ("flag".equalsIgnoreCase(action)) {
            toggleFlag(targetBoard, coord);
            result = "flag";
        } else {
            if (targetBoard.getRevealed().contains(coord)) {
                result = "safe";
            } else {
                targetBoard.getRevealed().add(coord);
                if (targetBoard.getBombs().contains(coord)) {
                    result = "bomb";
                    player.setHealth(Math.max(0, player.getHealth() - 1));
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

        if (hasRevealedAllSafeCells(targetBoard)) {
            mr.setGameOver(true);
            mr.setWinnerId(userId);
            match.setStatus("FINISHED");
            match.setWinnerId(userId);
            match.setFinishedAt(Instant.now());
            matchRepository.save(match);
            return mr;
        }

        if (player.getHealth() == 0) {
            mr.setGameOver(true);
            mr.setWinnerId(match.getPlayers().stream()
                    .filter(p -> !p.getUserId().equals(userId))
                    .findFirst()
                    .map(Player::getUserId).orElse(null));
            match.setStatus("FINISHED");
            match.setWinnerId(mr.getWinnerId());
            match.setFinishedAt(Instant.now());
            matchRepository.save(match);
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
        Match match = getMatchById(matchId);
        if (match == null) return;

        match.getPlayers().removeIf(p -> p.getUserId().equals(userId));
        if (match.getPlayers().isEmpty()) {
            match.setStatus("cancelled");
        }
        matchRepository.save(match);
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
}
