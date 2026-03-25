package com.nhomgame.web.auth;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nhomgame.domain.auth.User;
import com.nhomgame.service.auth.AuthService;
import com.nhomgame.web.dto.ApiResponse;

@RestController
@RequestMapping("/api")
public class LeaderboardController {

    private final AuthService authService;

    public LeaderboardController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<ApiResponse<LeaderboardResponse>> getLeaderboard(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(HttpStatus.UNAUTHORIZED.value(), false, "Unauthorized", null));
        }

        User currentUser = authService.findByEmail(principal.getName());
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(HttpStatus.NOT_FOUND.value(), false, "User not found", null));
        }

        List<User> topUsers = authService.getLeaderboardTop10();
        List<LeaderboardPlayer> topPlayers = topUsers.stream().map(this::toLeaderboardPlayer).collect(Collectors.toList());

        long higherRankCount = authService.countUsersAboveRank(currentUser.getRank());
        int myPosition = (int) higherRankCount + 1;

        LeaderboardResponse data = new LeaderboardResponse(topPlayers, currentUser.getRank(), myPosition);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), true, "Leaderboard fetched", data));
    }

    private LeaderboardPlayer toLeaderboardPlayer(User user) {
        return new LeaderboardPlayer(
                user.getId(),
                user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getUsername(),
                user.getAvatarUrl(),
                user.getRank(),
                user.getWins(),
                user.getLosses(),
                Math.round(user.getWinRate() * 10.0) / 10.0
        );
    }

    public static class LeaderboardPlayer {
        private String userId;
        private String displayName;
        private String avatar;
        private int rank;
        private int wins;
        private int losses;
        private double winRate;

        public LeaderboardPlayer() {
        }

        public LeaderboardPlayer(String userId, String displayName, String avatar, int rank, int wins, int losses, double winRate) {
            this.userId = userId;
            this.displayName = displayName;
            this.avatar = avatar;
            this.rank = rank;
            this.wins = wins;
            this.losses = losses;
            this.winRate = winRate;
        }

        public String getUserId() {
            return userId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getAvatar() {
            return avatar;
        }

        public int getRank() {
            return rank;
        }

        public int getWins() {
            return wins;
        }

        public int getLosses() {
            return losses;
        }

        public double getWinRate() {
            return winRate;
        }
    }

    public static class LeaderboardResponse {
        private List<LeaderboardPlayer> topPlayers;
        private int userRank;
        private int userPosition;

        public LeaderboardResponse() {
        }

        public LeaderboardResponse(List<LeaderboardPlayer> topPlayers, int userRank, int userPosition) {
            this.topPlayers = topPlayers;
            this.userRank = userRank;
            this.userPosition = userPosition;
        }

        public List<LeaderboardPlayer> getTopPlayers() {
            return topPlayers;
        }

        public int getUserRank() {
            return userRank;
        }

        public int getUserPosition() {
            return userPosition;
        }
    }
}
