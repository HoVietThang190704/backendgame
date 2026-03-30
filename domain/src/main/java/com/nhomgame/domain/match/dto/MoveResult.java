package com.nhomgame.domain.match.dto;

import java.util.ArrayList;
import java.util.List;

public class MoveResult {
    private String userId;
    private int x;
    private int y;
    private String action;
    private String result;
    private int health;
    private boolean gameOver;
    private String winnerId;
    private boolean shieldBlocked;
    private boolean shieldAvailable;
    private int winnerEloDelta;
    private int loserEloDelta;
    private List<RevealedCellResult> revealedCells = new ArrayList<>();

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
    public boolean isShieldBlocked() { return shieldBlocked; }
    public void setShieldBlocked(boolean shieldBlocked) { this.shieldBlocked = shieldBlocked; }
    public boolean isShieldAvailable() { return shieldAvailable; }
    public void setShieldAvailable(boolean shieldAvailable) { this.shieldAvailable = shieldAvailable; }
    public int getWinnerEloDelta() { return winnerEloDelta; }
    public void setWinnerEloDelta(int winnerEloDelta) { this.winnerEloDelta = winnerEloDelta; }
    public int getLoserEloDelta() { return loserEloDelta; }
    public void setLoserEloDelta(int loserEloDelta) { this.loserEloDelta = loserEloDelta; }
    public List<RevealedCellResult> getRevealedCells() { return revealedCells; }
    public void setRevealedCells(List<RevealedCellResult> revealedCells) {
        this.revealedCells = revealedCells == null ? new ArrayList<>() : revealedCells;
    }

    public static class RevealedCellResult {
        private int x;
        private int y;
        private int adjacentMines;

        public RevealedCellResult() {
        }

        public RevealedCellResult(int x, int y, int adjacentMines) {
            this.x = x;
            this.y = y;
            this.adjacentMines = adjacentMines;
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

        public int getAdjacentMines() {
            return adjacentMines;
        }

        public void setAdjacentMines(int adjacentMines) {
            this.adjacentMines = adjacentMines;
        }
    }
}