package com.nhomgame.service.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.nhomgame.domain.auth.RefreshToken;
import com.nhomgame.domain.auth.Role;
import com.nhomgame.domain.auth.User;
import com.nhomgame.domain.auth.dto.LoginRequest;
import com.nhomgame.domain.auth.dto.SignupRequest;
import com.nhomgame.infrastructure.auth.RefreshTokenRepository;
import com.nhomgame.infrastructure.auth.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Caching(put = {
            @CachePut(value = "users", key = "#result.id"),
            @CachePut(value = "users", key = "#result.username")
    })
    public User register(SignupRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        Set<Role> roles = new HashSet<>();
        if (req.getRoles() != null) {
            for (String r : req.getRoles()) {
                try {
                    roles.add(Role.valueOf(r));
                } catch (Exception ignored) {
                }
            }
        }
        if (roles.isEmpty()) roles.add(Role.user);

        User user = new User(req.getUsername(), req.getEmail(), passwordEncoder.encode(req.getPassword()), roles);
        user.setName(req.getName() != null ? req.getName() : req.getUsername());
        user.setAvatarUrl(req.getAvatarUrl() != null ? req.getAvatarUrl() : "");
        user.setActive(req.getIsActive() == null ? true : req.getIsActive());
        user.setRule(roles.stream().findFirst().map(Role::name).orElse("user"));
        user.setCurrentMatchId(null);
        user.setModifiedAt(Instant.now());

        return userRepository.save(user);
    }

    @CacheEvict(value = "users", key = "#req.email")
    public String authenticate(LoginRequest req) {
        long start = System.nanoTime();
        String email = null;
        try {
            email = req.getEmail();
            if (email == null) {
                throw new IllegalArgumentException("Invalid email or password");
            }
            User user = findByEmail(email);
            if (user == null) {
                throw new IllegalArgumentException("Invalid email or password");
            }
            if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Invalid email or password");
            }

            Instant now = Instant.now();
            user.setLastLogin(now);
            user.setModifiedAt(now);
            userRepository.save(user);

            return jwtService.generateAccessToken(user);
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.debug("authenticate(email={}) took {} ms", email, elapsedMs);
        }
    }

    // helper methods used by web layer
    @Cacheable(value = "users", key = "#username")
    public User findByUsername(@NonNull String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    @Cacheable(value = "users", key = "#email")
    public User findByEmail(@NonNull String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    @Cacheable(value = "users", key = "#id")
    public User findById(@NonNull String id) {
        return userRepository.findById(id).orElse(null);
    }

    @Caching(put = {
            @CachePut(value = "users", key = "#user.id"),
            @CachePut(value = "users", key = "#user.username"),
            @CachePut(value = "users", key = "#user.email")
    })
    public User saveUser(@NonNull User user) {
        user.setModifiedAt(Instant.now());
        return userRepository.save(user);
    }

    public boolean checkPassword(String raw, String encoded) {
        return passwordEncoder.matches(raw, encoded);
    }

    public RefreshToken createRefreshToken(String userId, long daysValid) {
        String token = jwtService.generateRefreshToken();
        RefreshToken rt = new RefreshToken(userId, token, Instant.now().plus(daysValid, ChronoUnit.DAYS));
        return refreshTokenRepository.save(rt);
    }

    public RefreshToken verifyRefreshToken(String token) {
        RefreshToken rt = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));
        if (rt.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(rt);
            throw new IllegalArgumentException("Refresh token expired");
        }
        return rt;
    }

    public void deleteRefreshTokensForUser(String userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    public java.util.List<User> getLeaderboardTop10() {
        return userRepository.findTop10ByOrderByRankDesc();
    }

    public long countUsersAboveRank(int rank) {
        return userRepository.countByRankGreaterThan(rank);
    }
}
