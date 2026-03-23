package com.nhomgame.web.match;

import java.security.Principal;
import java.util.stream.Collectors;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import com.nhomgame.domain.match.Match;
import com.nhomgame.domain.match.dto.JoinRoomPayload;
import com.nhomgame.domain.match.dto.SendMovePayload;
import com.nhomgame.domain.match.dto.ToggleReadyPayload;
import com.nhomgame.domain.match.dto.WsEvent;
import com.nhomgame.service.auth.AuthService;
import com.nhomgame.service.match.MatchService;

@Controller
public class MatchWebSocketController {

    private final SimpMessagingTemplate template;
    private final MatchService matchService;
    private final AuthService authService;

    public MatchWebSocketController(SimpMessagingTemplate template,
                                    MatchService matchService,
                                    AuthService authService) {
        this.template = template;
        this.matchService = matchService;
        this.authService = authService;
    }

    @MessageMapping("/join_room")
    @Transactional
    public void joinRoom(@Payload JoinRoomPayload payload, Principal principal) {
        String userId = principal.getName();
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
        String userId = principal.getName();
        String matchId = payload.getMatchId();
        if (matchId == null || matchId.isBlank()) return;

        var match = matchService.setPlayerReady(matchId, userId, payload.isReady());
        template.convertAndSend("/topic/match." + matchId,
                new WsEvent<>("toggle_ready", userId));

        if (match.getPlayers().size() >= 2 && match.getPlayers().stream().allMatch(p -> p.isReady())) {
            startGame(matchId);
        }
    }

    @MessageMapping("/send_move")
    public void sendMove(@Payload SendMovePayload payload, Principal principal) {
        String userId = principal.getName();
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
    }
}