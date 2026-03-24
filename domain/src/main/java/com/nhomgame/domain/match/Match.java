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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Player {
        @Field(targetType = FieldType.OBJECT_ID)
        private String userId;
        private String username;
        private boolean ready;
        private int health;

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
}
