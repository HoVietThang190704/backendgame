package com.nhomgame.domain.match;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "match_history")
public class MatchHistory {
    @Id
    private String id;

    @Field("matchId")
    private String matchId;

    @Field("players")
    private List<PlayerHistoryStats> players;

    @Field("matchType")
    private String matchType;

    @Field("duration")
    private long duration; // in seconds

    @Field("endReason")
    private String endReason;

    @Field("playedAt")
    private Instant playedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerHistoryStats {
        private String userId;
        private String username;
        private String result; // "win", "lose", "draw"
        private int finalHealth;
        private GameStats stats;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameStats {
        private int bombsPlaced;
        private int bombsFound;
        private int flagsPlaced;
        private int turnsPlayed;
    }
}
