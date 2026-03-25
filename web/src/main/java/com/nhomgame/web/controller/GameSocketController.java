package com.nhomgame.web.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import com.nhomgame.domain.match.Coordinate;
import com.nhomgame.domain.match.Match;
import com.nhomgame.domain.match.dto.WsEvent;
import com.nhomgame.infrastructure.match.MatchRepository;
import com.nhomgame.service.match.GameLogicService;

@Controller
public class GameSocketController {

    private final GameLogicService gameLogicService;
    private final MatchRepository matchRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public GameSocketController(
            GameLogicService gameLogicService,
            MatchRepository matchRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.gameLogicService = gameLogicService;
        this.matchRepository = matchRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/match.placeBombs")
    public void placeBombs(@Payload PlaceBombsPayload payload) {
        validatePlaceBombsPayload(payload);
        gameLogicService.placeBombs(payload.getMatchId(), payload.getUserId(), payload.getBombs());
    }

    @MessageMapping("/match.revealCell")
    public void revealCell(@Payload RevealCellPayload payload) {
        validateRevealCellPayload(payload);
        gameLogicService.revealCell(payload.getMatchId(), payload.getUserId(), payload.getX(), payload.getY());
    }

    @MessageMapping("/match.toggleFlag")
    @Transactional
    public void toggleFlag(@Payload ToggleFlagPayload payload) {
        validateToggleFlagPayload(payload);

        Match match = matchRepository.findById(payload.getMatchId())
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + payload.getMatchId()));

        if (match.getGameBoard() == null) {
            throw new IllegalStateException("Game board is not initialized");
        }

        Match.GameBoard board = match.getGameBoard().get(payload.getUserId());
        if (board == null) {
            throw new IllegalArgumentException("Game board not found for user: " + payload.getUserId());
        }

        if (board.getFlags() == null) {
            board.setFlags(new ArrayList<>());
        }

        String flagKey = toCoordKey(payload.getX(), payload.getY());
        List<String> flags = board.getFlags();
        if (flags.contains(flagKey)) {
            flags.remove(flagKey);
        } else {
            flags.add(flagKey);
        }

        Match savedMatch = matchRepository.save(match);
        messagingTemplate.convertAndSend(
                "/topic/match/" + payload.getMatchId(),
                new WsEvent<>("FLAG_TOGGLED", savedMatch.getGameBoard()));
    }

    private void validatePlaceBombsPayload(PlaceBombsPayload payload) {
        if (payload == null || isBlank(payload.getMatchId()) || isBlank(payload.getUserId()) || payload.getBombs() == null) {
            throw new IllegalArgumentException("matchId, userId, and bombs are required");
        }
    }

    private void validateRevealCellPayload(RevealCellPayload payload) {
        if (payload == null || isBlank(payload.getMatchId()) || isBlank(payload.getUserId())) {
            throw new IllegalArgumentException("matchId and userId are required");
        }
    }

    private void validateToggleFlagPayload(ToggleFlagPayload payload) {
        if (payload == null || isBlank(payload.getMatchId()) || isBlank(payload.getUserId())) {
            throw new IllegalArgumentException("matchId and userId are required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String toCoordKey(int x, int y) {
        return x + "," + y;
    }

    public static class PlaceBombsPayload {
        private String matchId;
        private String userId;
        private List<Coordinate> bombs;

        public PlaceBombsPayload() {
        }

        public String getMatchId() {
            return matchId;
        }

        public void setMatchId(String matchId) {
            this.matchId = matchId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public List<Coordinate> getBombs() {
            return bombs;
        }

        public void setBombs(List<Coordinate> bombs) {
            this.bombs = bombs;
        }
    }

    public static class RevealCellPayload {
        private String matchId;
        private String userId;
        private int x;
        private int y;

        public RevealCellPayload() {
        }

        public String getMatchId() {
            return matchId;
        }

        public void setMatchId(String matchId) {
            this.matchId = matchId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
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

    public static class ToggleFlagPayload {
        private String matchId;
        private String userId;
        private int x;
        private int y;

        public ToggleFlagPayload() {
        }

        public String getMatchId() {
            return matchId;
        }

        public void setMatchId(String matchId) {
            this.matchId = matchId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
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
