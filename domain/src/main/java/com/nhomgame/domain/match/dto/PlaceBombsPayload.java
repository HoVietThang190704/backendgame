package com.nhomgame.domain.match.dto;

import java.util.ArrayList;
import java.util.List;

public class PlaceBombsPayload {
    private String matchId;
    private List<Cell> bombs = new ArrayList<>();

    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    public List<Cell> getBombs() {
        return bombs;
    }

    public void setBombs(List<Cell> bombs) {
        this.bombs = bombs;
    }

    public static class Cell {
        private int x;
        private int y;

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
    }
}
