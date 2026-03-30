package com.nhomgame.web.match;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import com.nhomgame.domain.match.Match;
import com.nhomgame.domain.match.Coordinate;
import com.nhomgame.domain.match.dto.JoinRoomPayload;
import com.nhomgame.domain.match.dto.SendMovePayload;
import com.nhomgame.domain.match.dto.ToggleReadyPayload;
import com.nhomgame.domain.match.dto.WsEvent;
import com.nhomgame.service.auth.AuthService;
import com.nhomgame.service.match.GameLogicService;
import com.nhomgame.service.match.MatchService;

@Controller
public class MatchWebSocketController {

    private final SimpMessagingTemplate template;
    private final MatchService matchService;
    private final AuthService authService;
    private final GameLogicService gameLogicService;

    public MatchWebSocketController(SimpMessagingTemplate template,
                                    MatchService matchService,
                                    AuthService authService,
                                    GameLogicService gameLogicService) {
        this.template = template;
        this.matchService = matchService;
        this.authService = authService;
        this.gameLogicService = gameLogicService;
    }

    @MessageMapping("/join_room")
    @Transactional
    public void joinRoom(@Payload JoinRoomPayload payload, Principal principal) {
        String userId = resolveUserId(principal);
        if (userId == null) {
            return;
        }
        String matchId = payload.getMatchId();
        if (matchId == null || matchId.isBlank()) return;

        Match match = matchService.getMatchById(matchId);
        if (match == null) return;
        if (match.getStatus().equals("playing")
                || match.getStatus().equals("waiting")) {
            if (authService.findById(userId).getCurrentMatchId() != null
                    && !authService.findById(userId).getCurrentMatchId().equals(matchId)) {
                return;
            }
        }

        matchService.addPlayer(matchId, userId);
        template.convertAndSend("/topic/match." + matchId,
                new WsEvent<>("player_joined", userId));

        var readyStates = match.getPlayers().stream()
                .map(p -> new Object[] {p.getUserId(), p.isReady()})
                .collect(Collectors.toList());
        template.convertAndSend("/topic/match." + matchId,
                new WsEvent<>("ready_update", readyStates));

        if (match.getPlayers().size() >= 2 && match.getPlayers().stream().allMatch(p -> p.isReady())) {
            startGame(matchId);
        }
    }

    private void startGame(String matchId) {
        Match match = matchService.startMatch(matchId);
        if (match == null) return;
        template.convertAndSend("/topic/match." + matchId,
                new WsEvent<>("start_game", new Object() {
                    public final String matchId2 = matchId;
                    public final String currentTurn = match.getCurrentPlayerId();
                    public final int turnTimeLimit = match.getTurnTimeLimit();
                }));
    }

    @MessageMapping("/toggle_ready")
    public void toggleReady(@Payload ToggleReadyPayload payload, Principal principal) {
        String userId = resolveUserId(principal);
        if (userId == null) {
            return;
        }
        String matchId = payload.getMatchId();
        if (matchId == null || matchId.isBlank()) return;

        var match = matchService.setPlayerReady(matchId, userId, payload.isReady());
        template.convertAndSend("/topic/match." + matchId,
                new WsEvent<>("toggle_ready", userId));

        if (match.getPlayers().size() >= 2 && match.getPlayers().stream().allMatch(p -> p.isReady())) {
            startGame(matchId);
        }
    }

    @MessageMapping("/place_bombs")
    @Transactional
    public void placeBombs(@Payload BombSetupPayload payload, Principal principal) {
        String userId = resolveUserId(principal, payload != null ? payload.getUserId() : null);
        if (userId == null || payload == null || payload.getMatchId() == null || payload.getMatchId().isBlank()) {
            return;
        }

        List<Coordinate> bombs = payload.getBombs() != null ? payload.getBombs() : List.of();
        gameLogicService.placeBombs(payload.getMatchId(), userId, bombs);

        Match updated = matchService.getMatchById(payload.getMatchId());
        if (updated != null && "PLAYING".equalsIgnoreCase(updated.getStatus())) {
            template.convertAndSend("/topic/match." + payload.getMatchId(),
                    new WsEvent<>("start_game", new Object() {
                        public final String matchId = payload.getMatchId();
                        public final String currentTurn = updated.getCurrentPlayerId();
                        public final Integer turnTimeLimit = updated.getTurnTimeLimit();
                    }));
        }
    }

    @MessageMapping("/send_move")
    public void sendMove(@Payload SendMovePayload payload, Principal principal) {
        String userId = resolveUserId(principal, extractPayloadUserId(payload));
        if (userId == null) {
            return;
        }
        String matchId = payload.getMatchId();
        if (matchId == null || matchId.isBlank()) return;

        var match = matchService.getMatchById(matchId);
        if (match == null) return;
        if (!userId.equals(match.getCurrentPlayerId())) return;

        var result = matchService.applyMove(matchId, userId, payload.getX(), payload.getY(), payload.getAction());
        template.convertAndSend("/topic/match." + matchId,
                new WsEvent<>("move_result", result));

        if (result.isGameOver()) {
            template.convertAndSend("/topic/match." + matchId,
                    new WsEvent<>("game_over", result));
            return;
        }
        matchService.switchTurn(matchId);

        Match switched = matchService.getMatchById(matchId);
        if (switched != null) {
            template.convertAndSend("/topic/match." + matchId,
                    new WsEvent<>("turn_switched", new Object() {
                        public final String currentTurn = switched.getCurrentPlayerId();
                        public final Integer turnTimeLimit = switched.getTurnTimeLimit();
                    }));
        }
    }

    private String resolveUserId(Principal principal, String payloadUserId) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            if (payloadUserId != null && !payloadUserId.isBlank()) {
                return payloadUserId;
            }
            return null;
        }
        var user = authService.findByEmail(principal.getName());
        if (user != null && user.getId() != null && !user.getId().isBlank()) {
            return user.getId();
        }
        if (payloadUserId != null && !payloadUserId.isBlank()) {
            return payloadUserId;
        }
        return null;
    }

    private String resolveUserId(Principal principal) {
        return resolveUserId(principal, null);
    }

    private String extractPayloadUserId(SendMovePayload payload) {
        if (payload == null) {
            return null;
        }

        try {
            Object value = payload.getClass().getMethod("getUserId").invoke(payload);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (Exception ignored) {
            // Ignore and fallback to principal-based resolution.
        }

        return null;
    }

    public static class BombSetupPayload {
        private String matchId;
        private String userId;
        private List<Coordinate> bombs;

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
}