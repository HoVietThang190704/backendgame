package com.nhomgame.domain.match.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for POST /api/match/find request
 */
public class MatchFindRequest {
    @NotBlank(message = "boardSize is required")
    private String boardSize; // "small", "medium", "large"

    public MatchFindRequest() {
    }

    public MatchFindRequest(String boardSize) {
        this.boardSize = boardSize;
    }

    public String getBoardSize() {
        return boardSize;
    }

    public void setBoardSize(String boardSize) {
        this.boardSize = boardSize;
    }
}
