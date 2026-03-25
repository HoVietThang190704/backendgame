package com.nhomgame.service.match;

import java.time.Instant;
import java.util.Random;

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
        log.info("User {} requesting to find match with boardSize: {}", userId, request.getBoardSize());

        // 1. Get user by ID
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 2. Check if user is already in a match
        if (user.getCurrentMatchId() != null && !user.getCurrentMatchId().isEmpty()) {
            throw new IllegalArgumentException("User is already in an active match");
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
        Preferences preferences = new Preferences(request.getBoardSize());
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
        if (user.getCurrentMatchId() != null && !user.getCurrentMatchId().isEmpty()) {
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

        String result = Math.random() < 0.15 ? "bomb" : "safe";

        Player player = match.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst().orElse(null);
        if (player == null) return null;

        if ("bomb".equals(result)) {
            player.setHealth(Math.max(0, player.getHealth() - 1));
        }

        matchRepository.save(match);

        com.nhomgame.domain.match.dto.MoveResult mr = new com.nhomgame.domain.match.dto.MoveResult();
        mr.setUserId(userId);
        mr.setX(x);
        mr.setY(y);
        mr.setAction(action);
        mr.setResult(result);
        mr.setHealth(player.getHealth());

        if (player.getHealth() == 0) {
            mr.setGameOver(true);
            mr.setWinnerId(match.getPlayers().stream()
                    .filter(p -> !p.getUserId().equals(userId))
                    .findFirst()
                    .map(Player::getUserId).orElse(null));
            match.setStatus("finished");
            match.setWinnerId(mr.getWinnerId());
            matchRepository.save(match);
        }

        return mr;
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
