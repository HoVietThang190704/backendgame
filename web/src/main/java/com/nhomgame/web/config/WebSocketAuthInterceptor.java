package com.nhomgame.web.config;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.nhomgame.service.auth.JwtService;

public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public WebSocketAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest)) return false;

        String token = ((ServletServerHttpRequest) request)
                .getServletRequest().getParameter("token");
        if (token == null) {
            var headers = request.getHeaders().get("Sec-WebSocket-Protocol");
            if (headers != null && !headers.isEmpty()) {
                token = headers.get(0);
            }
        }
        if (token == null || !jwtService.validateToken(token)) return false;

        String userId = jwtService.getUsernameFromToken(token);
        attributes.put("userId", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}