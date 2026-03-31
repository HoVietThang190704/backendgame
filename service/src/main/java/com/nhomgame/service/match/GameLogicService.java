package com.nhomgame.service.match;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nhomgame.domain.match.Coordinate;
import com.nhomgame.domain.match.Match;
import com.nhomgame.domain.match.dto.WsEvent;
import com.nhomgame.infrastructure.match.MatchRepository;

@Service
public class GameLogicService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GameLogicService.class);

    private final MatchRepository matchRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public GameLogicService(MatchRepository matchRepository, SimpMessagingTemplate messagingTemplate) {
        this.matchRepository = matchRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    @SuppressWarnings("rawtypes")
    public void placeBombs(String mid, String userId, List bombs) {
        Match match = matchRepository.findById(mid)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + mid));

        if (!"PREPARATION".equalsIgnoreCase(match.getStatus())) {
            throw new IllegalStateException("Match is not in PREPARATION status");
        }

        String opponentId = resolveOpponentId(match, userId);
        Match.GameBoard opponentBoard = getOrCreateBoard(match, opponentId);
        List<String> normalizedBombs = normalizeBombs(bombs);

        opponentBoard.setBombs(normalizedBombs);
        ensureListsInitialized(opponentBoard);

        boolean bothPlacedBombs = haveBothPlayersPlacedBombs(match);
        if (bothPlacedBombs) {
            match.setStatus("PLAYING");
            if (match.getCurrentPlayerId() == null && match.getPlayers() != null && !match.getPlayers().isEmpty()) {
                match.setCurrentPlayerId(match.getPlayers().get(0).getUserId());
            }
            match.setTurnStartTime(Instant.now());
        }

        Match savedMatch = matchRepository.save(match);

        if (bothPlacedBombs) {
            messagingTemplate.convertAndSend(
                    "/topic/match/" + mid,
                    new WsEvent<>("start_game", new Object() {
                        public final String matchId = mid;
                        public final String currentTurn = savedMatch.getCurrentPlayerId();
                        public final int turnTimeLimit = savedMatch.getTurnTimeLimit();
                    }));
        }
    }

    @Transactional
    public void revealCell(String matchId, String userId, int x, int y) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        if (!"PLAYING".equalsIgnoreCase(match.getStatus())) {
            throw new IllegalStateException("Match is not in PLAYING status");
        }

        Match.GameBoard userBoard = getOrCreateBoard(match, userId);
        ensureListsInitialized(userBoard);

        int width = resolveBoardWidth(userBoard);
        int height = resolveBoardHeight(userBoard);
        validateInsideBoard(x, y, width, height);

        String key = toCoordKey(x, y);
        if (isAlreadyRevealed(userBoard, x, y)) {
            return;
        }

        Set<String> bombSet = new HashSet<>(safeList(userBoard.getBombs()));
        if (bombSet.contains(key)) {
            int nextHearts = Math.max(0, safeInt(userBoard.getHearts(), 3) - 1);
            userBoard.setHearts(nextHearts);
            addRevealedCell(userBoard, x, y, -1);
        } else {
            int adjacentBombs = countAdjacentBombs(x, y, bombSet, width, height);
            if (adjacentBombs > 0) {
                addRevealedCell(userBoard, x, y, adjacentBombs);
            } else {
                floodFillReveal(userBoard, x, y, bombSet, width, height);
            }
        }

        applyWinLoss(match, userId, userBoard, width, height, bombSet);

        Match savedMatch = matchRepository.save(match);
        messagingTemplate.convertAndSend(
                "/topic/match/" + matchId,
                new WsEvent<>("MOVE_RESULT", savedMatch.getGameBoard()));
    }

    private String resolveOpponentId(Match match, String userId) {
        return match.getPlayers().stream()
                .map(Match.Player::getUserId)
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Opponent not found for user: " + userId));
    }

    private Match.GameBoard getOrCreateBoard(Match match, String playerId) {
        if (match.getGameBoard() == null) {
            match.setGameBoard(new java.util.HashMap<>());
        }

        Map<String, Match.GameBoard> boards = match.getGameBoard();
        Match.GameBoard board = boards.get(playerId);
        if (board == null) {
            board = new Match.GameBoard();
            board.setHearts(3);
            board.setBombs(new ArrayList<>());
            board.setFlags(new ArrayList<>());
            board.setRevealed(new ArrayList<>());
            boards.put(playerId, board);
        }
        ensureListsInitialized(board);
        if (board.getHearts() == null) {
            board.setHearts(3);
        }
        return board;
    }

    private void ensureListsInitialized(Match.GameBoard board) {
        if (board.getBombs() == null) {
            board.setBombs(new ArrayList<>());
        }
        if (board.getFlags() == null) {
            board.setFlags(new ArrayList<>());
        }
        if (board.getRevealed() == null) {
            board.setRevealed(new ArrayList<>());
        }
    }

    private boolean haveBothPlayersPlacedBombs(Match match) {
        if (match.getPlayers() == null || match.getPlayers().size() < 2) {
            return false;
        }

        for (Match.Player player : match.getPlayers()) {
            Match.GameBoard board = getOrCreateBoard(match, player.getUserId());
            if (board.getBombs() == null || board.getBombs().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("rawtypes")
    private List<String> normalizeBombs(List bombs) {
        List<String> normalized = new ArrayList<>();
        if (bombs == null) {
            return normalized;
        }

        for (Object raw : bombs) {
            String key = toCoordKey(raw);
            if (key != null && !normalized.contains(key)) {
                normalized.add(key);
            }
        }
        return normalized;
    }

    private String toCoordKey(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String str) {
            String trimmed = str.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (raw instanceof Coordinate c) {
            return toCoordKey(c.getX(), c.getY());
        }
        if (raw instanceof Map<?, ?> map) {
            Object x = map.get("x");
            Object y = map.get("y");
            if (x instanceof Number nx && y instanceof Number ny) {
                return toCoordKey(nx.intValue(), ny.intValue());
            }
        }
        return null;
    }

    private String toCoordKey(int x, int y) {
        return x + "," + y;
    }

    private String toRevealedEntry(int x, int y, int value) {
        return x + "," + y + ":" + value;
    }

    private boolean isAlreadyRevealed(Match.GameBoard board, int x, int y) {
        String prefix = toCoordKey(x, y) + ":";
        return safeList(board.getRevealed()).stream().anyMatch(r -> r != null && r.startsWith(prefix));
    }

    private void addRevealedCell(Match.GameBoard board, int x, int y, int value) {
        if (!isAlreadyRevealed(board, x, y)) {
            board.getRevealed().add(toRevealedEntry(x, y, value));
        }
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
                if (!inside(nx, ny, width, height)) {
                    continue;
                }
                if (bombSet.contains(toCoordKey(nx, ny))) {
                    count++;
                }
            }
        }
        return count;
    }

    private void floodFillReveal(Match.GameBoard board, int startX, int startY, Set<String> bombSet, int width, int height) {
        Queue<Coordinate> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        queue.add(new Coordinate(startX, startY));
        visited.add(toCoordKey(startX, startY));

        while (!queue.isEmpty()) {
            Coordinate current = queue.poll();
            int x = current.getX();
            int y = current.getY();

            if (!inside(x, y, width, height)) {
                continue;
            }
            String coordKey = toCoordKey(x, y);
            if (bombSet.contains(coordKey)) {
                continue;
            }

            int adjacent = countAdjacentBombs(x, y, bombSet, width, height);
            addRevealedCell(board, x, y, adjacent);

            if (adjacent != 0) {
                continue;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int nx = x + dx;
                    int ny = y + dy;
                    if (!inside(nx, ny, width, height)) {
                        continue;
                    }
                    String neighborKey = toCoordKey(nx, ny);
                    if (visited.add(neighborKey) && !bombSet.contains(neighborKey)) {
                        queue.add(new Coordinate(nx, ny));
                    }
                }
            }
        }
    }

    private void applyWinLoss(Match match, String userId, Match.GameBoard board, int width, int height, Set<String> bombSet) {
        if (safeInt(board.getHearts(), 3) <= 0) {
            match.setStatus("FINISHED");
            match.setWinnerId(resolveOpponentId(match, userId));
            return;
        }

        int totalCells = width * height;
        int safeCells = Math.max(0, totalCells - bombSet.size());
        int revealedSafeCells = (int) safeList(board.getRevealed()).stream()
                .filter(Objects::nonNull)
                .filter(entry -> !entry.endsWith(":-1"))
                .count();

        if (revealedSafeCells >= safeCells && safeCells > 0) {
            match.setStatus("FINISHED");
            match.setWinnerId(userId);
            log.info("User {} wins match {} by revealing all safe cells", userId, match.getId());
        }
    }

    private boolean inside(int x, int y, int width, int height) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private void validateInsideBoard(int x, int y, int width, int height) {
        if (!inside(x, y, width, height)) {
            throw new IllegalArgumentException("Coordinate is outside board: (" + x + ", " + y + ")");
        }
    }

    private int resolveBoardWidth(Match.GameBoard board) {
        return safeInt(board.getWidth(), 10);
    }

    private int resolveBoardHeight(Match.GameBoard board) {
        return safeInt(board.getHeight(), 10);
    }

    private int safeInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private List<String> safeList(List<String> list) {
        return list == null ? List.of() : list;
    }
}
