package com.nhomgame.domain.match.dto;

public class MoveResult {
    private String userId;
    private int x;
    private int y;
    private String action;
    private String result;
    private int health;
    private boolean gameOver;
    private String winnerId;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public int getHealth() { return health; }
    public void setHealth(int health) { this.health = health; }
    public boolean isGameOver() { return gameOver; }
    public void setGameOver(boolean gameOver) { this.gameOver = gameOver; }
    public String getWinnerId() { return winnerId; }
    public void setWinnerId(String winnerId) { this.winnerId = winnerId; }
}