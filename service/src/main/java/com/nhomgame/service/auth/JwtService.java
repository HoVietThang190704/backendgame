package com.nhomgame.service.auth;

import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.nhomgame.domain.auth.User;

@Service
public class JwtService {

    private final Algorithm algorithm;
    private final long accessTokenValidityMs;

    public JwtService(@Value("${jwt.secret:default-secret}") String secret,
                      @Value("${jwt.accessExpirationMs:900000}") long accessTokenValidityMs) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.accessTokenValidityMs = accessTokenValidityMs;
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + accessTokenValidityMs);

        return JWT.create()
                .withSubject(user.getEmail())
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .withClaim("userId", user.getId())
                .withClaim("email", user.getEmail())
                .withClaim("rule", user.getRule())
                .withClaim("role", user.getRule())
                .withClaim("roles", user.getRoles().stream().map(Enum::name).toList())
                .sign(algorithm);
    }

    public String generateRefreshToken() {
        // simple random token (could be JWT as well)
        return UUID.randomUUID().toString();
    }

    public boolean validateToken(String token) {
        try {
            JWT.require(algorithm).build().verify(token);
            return true;
        } catch (JWTVerificationException ex) {
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        try {
            return JWT.require(algorithm).build().verify(token).getSubject();
        } catch (JWTVerificationException ex) {
            return null;
        }
    }
}
