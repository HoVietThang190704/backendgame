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
import com.nhomgame.domain.match.dto.MatchResultResponse;
import com.nhomgame.domain.match.dto.MatchResultResponse.PlayerResultInfo;
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

        // 2. Check if user is already in a match - nhưng phải verify match còn tồn tại
        if (user.getCurrentMatchId() != null && !user.getCurrentMatchId().isEmpty()) {
            Match activeMatch = matchRepository.findById(user.getCurrentMatchId()).orElse(null);
            // Nếu match tồn tại và còn active (waiting hoặc playing)
            if (activeMatch != null && ("waiting".equals(activeMatch.getStatus()) || "playing".equals(activeMatch.getStatus()))) {
                throw new IllegalArgumentException("User is already in an active match");
            }
            // Nếu match không tồn tại hoặc đã kết thúc, clear currentMatchId
            if (activeMatch == null || "finished".equals(activeMatch.getStatus()) || "cancelled".equals(activeMatch.getStatus())) {
                user.setCurrentMatchId(null);
                userRepository.save(user);
                log.info("Cleared stale currentMatchId for user {}", userId);
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

        // 2. Check if user is already in an active match - nhưng phải verify match còn tồn tại
        if (user.getCurrentMatchId() != null && !user.getCurrentMatchId().isEmpty()) {
            Match currentMatch = matchRepository.findById(user.getCurrentMatchId()).orElse(null);
            if (currentMatch != null && ("waiting".equalsIgnoreCase(currentMatch.getStatus()) || "playing".equalsIgnoreCase(currentMatch.getStatus()))) {
                throw new IllegalArgumentException("User is already in an active match");
            }
            // stale reference: match no longer exists or not active, cleanup and continue
            user.setCurrentMatchId(null);
            user.setModifiedAt(Instant.now());
            userRepository.save(user);
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

        String result = Math.random() < 0.15 ? "bomb" : "safe";

        Player player = match.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst().orElse(null);
        if (player == null) return null;

        if ("bomb".equals(result)) {
            player.setHealth(Math.max(0, player.getHealth() - 1));
        }

        // Append move history for game state reconstruction
        Match.Move recordedMove = new Match.Move(userId, x, y, action);
        match.getMoves().add(recordedMove);

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
