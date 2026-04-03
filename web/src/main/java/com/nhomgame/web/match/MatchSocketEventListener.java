package com.nhomgame.web.match;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.nhomgame.domain.match.dto.WsEvent;
import com.nhomgame.service.match.MatchService;

@Component
public class MatchSocketEventListener {

    private final SimpMessagingTemplate template;
    private final MatchService matchService;

    public MatchSocketEventListener(SimpMessagingTemplate template, MatchService matchService) {
        this.template = template;
        this.matchService = matchService;
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String userId = event.getUser() != null ? event.getUser().getName() : null;
        if (userId == null) {
            return;
        }

        var attributes = event.getMessage().getHeaders().get("simpSessionAttributes");
        if (!(attributes instanceof java.util.Map)) {
            return;
        }

        var attrMap = (java.util.Map<String, Object>) attributes;
        String matchId = (String) attrMap.get("matchId");
        if (matchId == null) {
            return;
        }

        matchService.markPlayerDisconnected(matchId, userId);
        template.convertAndSend("/topic/match." + matchId, new WsEvent<>("player_left", userId));
    }
}
