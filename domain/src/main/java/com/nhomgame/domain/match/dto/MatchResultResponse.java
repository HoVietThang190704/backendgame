package com.nhomgame.domain.match.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO for match result response
 * Contains detailed information about a finished match
 */
public class MatchResultResponse {
    private String matchId;
    private String status;
    private Instant startedAt;
    private Instant finishedAt;
    private long durationSeconds;
    private int totalMoves;
    private String winnerId;
    private String winnerUsername;
    private List<PlayerResultInfo> players;

    public MatchResultResponse() {
    }

    public MatchResultResponse(String matchId, String status, Instant startedAt, Instant finishedAt, 
                               int totalMoves, String winnerId, String winnerUsername, 
                               List<PlayerResultInfo> players) {
        this.matchId = matchId;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.totalMoves = totalMoves;
        this.winnerId = winnerId;
        this.winnerUsername = winnerUsername;
        this.players = players;
        
        // Calculate duration in seconds
        if (startedAt != null && finishedAt != null) {
            this.durationSeconds = java.time.temporal.ChronoUnit.SECONDS.between(startedAt, finishedAt);
        }
    }

    // Getters and Setters
    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }

    public int getTotalMoves() { return totalMoves; }
    public void setTotalMoves(int totalMoves) { this.totalMoves = totalMoves; }

    public String getWinnerId() { return winnerId; }
    public void setWinnerId(String winnerId) { this.winnerId = winnerId; }

    public String getWinnerUsername() { return winnerUsername; }
    public void setWinnerUsername(String winnerUsername) { this.winnerUsername = winnerUsername; }

    public List<PlayerResultInfo> getPlayers() { return players; }
    public void setPlayers(List<PlayerResultInfo> players) { this.players = players; }

    /**
     * Nested class for player result information
     */
    public static class PlayerResultInfo {
        private String userId;
        private String username;
        private int health;
        private int mineHits;
        private boolean alive;
        private int rankBefore;
        private int rankAfter;
        private int rankChange;
        private boolean winner;

        public PlayerResultInfo() {
        }

        public PlayerResultInfo(String userId, String username, int health, int mineHits, 
                               boolean alive, int rankBefore, int rankAfter, boolean winner) {
            this.userId = userId;
            this.username = username;
            this.health = health;
            this.mineHits = mineHits;
            this.alive = alive;
            this.rankBefore = rankBefore;
            this.rankAfter = rankAfter;
            this.rankChange = rankAfter - rankBefore;
            this.winner = winner;
        }

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public int getHealth() { return health; }
        public void setHealth(int health) { this.health = health; }

        public int getMineHits() { return mineHits; }
        public void setMineHits(int mineHits) { this.mineHits = mineHits; }

        public boolean isAlive() { return alive; }
        public void setAlive(boolean alive) { this.alive = alive; }

        public int getRankBefore() { return rankBefore; }
        public void setRankBefore(int rankBefore) { this.rankBefore = rankBefore; }

        public int getRankAfter() { return rankAfter; }
        public void setRankAfter(int rankAfter) { this.rankAfter = rankAfter; }

        public int getRankChange() { return rankChange; }
        public void setRankChange(int rankChange) { this.rankChange = rankChange; }

        public boolean isWinner() { return winner; }
        public void setWinner(boolean winner) { this.winner = winner; }
    }
}
