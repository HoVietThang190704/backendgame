package com.nhomgame.domain.match.dto;

public class ToggleReadyPayload {
    private String matchId;
    private boolean ready;

    public String getMatchId() {
        return matchId;
    }

    public void setMatchId(String matchId) {
        this.matchId = matchId;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }
}