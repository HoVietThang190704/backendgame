package com.nhomgame.domain.match.dto;

public class WsEvent<T> {
    private String type;
    private T payload;

    public WsEvent() {}

    public WsEvent(String type, T payload) {
        this.type = type;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }
}