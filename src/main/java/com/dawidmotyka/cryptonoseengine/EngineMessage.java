package com.dawidmotyka.cryptonoseengine;

/**
 * Created by dawid on 7/10/17.
 */
public class EngineMessage {

    public enum Type {
        CONNECTED,
        CONNECTING,
        DISCONNECTED,
        ERROR,
        INFO,
        NO_PAIRS
    }

    private Type type;
    private String message;

    public EngineMessage(Type type, String message) {
        this.type=type;
        this.message=message;
    }

    public Type getCode() {
        return type;
    }
    public String getMessage() {
        return message;
    }
}