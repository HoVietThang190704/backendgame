package com.nhomgame.domain.match;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * WaitingQueue domain document - represents a player waiting for a match.
 */
@Document(collection = "waiting_queues")
public class WaitingQueue {
    @Id
    private String id;
    private String userId;
    private int rank;
    private String status; // "waiting", "matched", "cancelled"
    private Instant joinedAt;
    private Preferences preferences;
    private String matchedWith; // userId of matched opponent
    private String matchId; // ID of created match
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public WaitingQueue() {
    }

    public WaitingQueue(String userId, int rank, Preferences preferences) {
        this.userId = userId;
        this.rank = rank;
        this.status = "waiting";
        this.joinedAt = Instant.now();
        this.preferences = preferences;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Preferences getPreferences() {
        return preferences;
    }

    public void setPreferences(Preferences preferences) {
        this.preferences = preferences;
    }

    public String getMatchedWith() {
        return matchedWith;
    }

    public void setMatchedWith(String matchedWith) {
        this.matchedWith = matchedWith;
    }

    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Nested class for preferences
     */
    public static class Preferences {
        private String boardSize; // "small", "medium", "large"

        public Preferences() {
        }

        public Preferences(String boardSize) {
            this.boardSize = boardSize;
        }

        public String getBoardSize() {
            return boardSize;
        }

        public void setBoardSize(String boardSize) {
            this.boardSize = boardSize;
        }
    }
}
