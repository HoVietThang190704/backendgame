package com.nhomgame.domain.match;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "matches")
public class Match {

    @Id
    private String id;
    private String matchType;
    private String status;
    private String pinCode;

    @Field(targetType = FieldType.OBJECT_ID)
    private String hostId;

    private List<Player> players = new ArrayList<>();

    private Map<String, GameBoard> gameBoard = new HashMap<>();

    private Instant turnStartTime;
    private List<Move> moves = new ArrayList<>();
    private Instant startedAt;
    private Instant finishedAt;

    private Integer currentTurn = 0;

    @Field(targetType = FieldType.OBJECT_ID)
    private String currentPlayerId;

    @Field(targetType = FieldType.OBJECT_ID)
    private String winnerId;

    private Integer turnTimeLimit;

    private Instant createdAt;
    private Instant updatedAt;

    public Match(String matchType, String hostId, String pinCode) {
        this.matchType = matchType;
        this.hostId = hostId;
        this.pinCode = pinCode;
        this.status = "waiting";
        this.turnTimeLimit = 30;
        this.currentTurn = 0;
        this.players = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Nested class for Player in the match
     */
    @Data
    @NoArgsConstructor
    public static class Player {
        @Field(targetType = FieldType.OBJECT_ID)
        private String userId;
        private String username;
        private boolean ready;
        private int health;
        private boolean shieldAvailable = true;

        public Player(String userId, String username) {
            this.userId = userId;
            this.username = username;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameBoard {
        private Integer width;
        private Integer height;
        private Integer mineCount;
        private String difficulty;
        private Integer hearts = 3;
        private List<String> bombs = new ArrayList<>();
        private List<String> flags = new ArrayList<>();
        private List<String> revealed = new ArrayList<>();
    }

    public static class Move {
        private String playerId;
        private int x;
        private int y;
        private String action; // open or flag
        private Instant createdAt = Instant.now();

        public Move() {
        }

        public Move(String playerId, int x, int y, String action) {
            this.playerId = playerId;
            this.x = x;
            this.y = y;
            this.action = action;
            this.createdAt = Instant.now();
        }

        public String getPlayerId() {
            return playerId;
        }

        public void setPlayerId(String playerId) {
            this.playerId = playerId;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
    }
}

