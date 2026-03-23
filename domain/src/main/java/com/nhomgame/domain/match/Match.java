package com.nhomgame.domain.match;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Match domain document - represents a game match/room.
 */
@Document(collection = "matches")
public class Match {
    @Id
    private String id;
    private String matchType; // "private", "public", "ranked"
    private String status; // "waiting", "playing", "finished", "cancelled"
    private String pinCode; // 4-digit code for private rooms
    private String hostId; // User ID of the room creator
    private List<Player> players = new ArrayList<>();
    private GameBoard gameBoard;
    private int turnTimeLimit; // in seconds
    private int currentTurn; // 0-based index of current player
    private String currentPlayerId; // ID of player whose turn it is
    private Instant createdAt = Instant.now();
    private Instant startedAt;
    private Instant finishedAt;
    private String winnerId; // ID of winning player
    private Instant updatedAt = Instant.now();

    public Match() {
    }

    public Match(String matchType, String hostId, String pinCode) {
        this.matchType = matchType;
        this.hostId = hostId;
        this.pinCode = pinCode;
        this.status = "waiting";
        this.currentTurn = 0;
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

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public GameBoard getGameBoard() {
        return gameBoard;
    }

    public void setGameBoard(GameBoard gameBoard) {
        this.gameBoard = gameBoard;
    }

    public int getTurnTimeLimit() {
        return turnTimeLimit;
    }

    public void setTurnTimeLimit(int turnTimeLimit) {
        this.turnTimeLimit = turnTimeLimit;
    }

    public int getCurrentTurn() {
        return currentTurn;
    }

    public void setCurrentTurn(int currentTurn) {
        this.currentTurn = currentTurn;
    }

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public void setCurrentPlayerId(String currentPlayerId) {
        this.currentPlayerId = currentPlayerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(String winnerId) {
        this.winnerId = winnerId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Nested class for Player in the match
     */
    public static class Player {
        private String userId;
        private String username;
        private int score = 0;
        private int health = 3;
        private boolean isReady = false;
        private boolean isAlive = true;
        private Instant joinedAt;

        public Player() {
        }

        public Player(String userId, String username) {
            this.userId = userId;
            this.username = username;
            this.joinedAt = Instant.now();
        }

        // Getters and Setters
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }

        public int getHealth() {
            return health;
        }

        public void setHealth(int health) {
            this.health = health;
        }

        public boolean isReady() {
            return isReady;
        }

        public void setReady(boolean ready) {
            isReady = ready;
        }

        public boolean isAlive() {
            return isAlive;
        }

        public void setAlive(boolean alive) {
            isAlive = alive;
        }

        public Instant getJoinedAt() {
            return joinedAt;
        }

        public void setJoinedAt(Instant joinedAt) {
            this.joinedAt = joinedAt;
        }
    }

    /**
     * Nested class for GameBoard configuration
     */
    public static class GameBoard {
        private int width;
        private int height;
        private int mineCount;
        private String difficulty; // "easy", "medium", "hard"

        public GameBoard() {
        }

        public GameBoard(int width, int height, int mineCount, String difficulty) {
            this.width = width;
            this.height = height;
            this.mineCount = mineCount;
            this.difficulty = difficulty;
        }

        // Getters and Setters
        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getMineCount() {
            return mineCount;
        }

        public void setMineCount(int mineCount) {
            this.mineCount = mineCount;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(String difficulty) {
            this.difficulty = difficulty;
        }
    }
}
