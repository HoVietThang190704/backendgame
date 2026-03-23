package com.nhomgame.domain.match.dto;

/**
 * DTO for POST /api/matches/create request (optional - can use defaults)
 */
public class CreateMatchRequest {
    private String matchType = "private"; // default: "private"
    private Integer boardWidth = 10; // default: 10
    private Integer boardHeight = 10; // default: 10
    private Integer mineCount = 20; // default: 20
    private String difficulty = "medium"; // default: "medium"
    private Integer turnTimeLimit = 30; // default: 30 seconds

    public CreateMatchRequest() {
    }

    public CreateMatchRequest(String matchType, Integer boardWidth, Integer boardHeight,
                             Integer mineCount, String difficulty, Integer turnTimeLimit) {
        this.matchType = matchType != null ? matchType : "private";
        this.boardWidth = boardWidth != null ? boardWidth : 10;
        this.boardHeight = boardHeight != null ? boardHeight : 10;
        this.mineCount = mineCount != null ? mineCount : 20;
        this.difficulty = difficulty != null ? difficulty : "medium";
        this.turnTimeLimit = turnTimeLimit != null ? turnTimeLimit : 30;
    }

    // Getters and Setters
    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public Integer getBoardWidth() {
        return boardWidth;
    }

    public void setBoardWidth(Integer boardWidth) {
        this.boardWidth = boardWidth;
    }

    public Integer getBoardHeight() {
        return boardHeight;
    }

    public void setBoardHeight(Integer boardHeight) {
        this.boardHeight = boardHeight;
    }

    public Integer getMineCount() {
        return mineCount;
    }

    public void setMineCount(Integer mineCount) {
        this.mineCount = mineCount;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getTurnTimeLimit() {
        return turnTimeLimit;
    }

    public void setTurnTimeLimit(Integer turnTimeLimit) {
        this.turnTimeLimit = turnTimeLimit;
    }
}
