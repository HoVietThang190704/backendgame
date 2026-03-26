package com.nhomgame.domain.match.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchHistoryDTO {
    private String matchId;
    private String matchType;
    private String result; // win, lose, draw
    private OpponentDTO opponent;
    private long duration; // in seconds
    private int eloChange;
    private Instant playedAt;
}
