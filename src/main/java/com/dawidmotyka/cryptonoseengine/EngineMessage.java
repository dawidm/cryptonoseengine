package com.dawidmotyka.cryptonoseengine;

/**
 * Created by dawid on 7/10/17.
 */
public class EngineMessage {

    public static final int CONNECTED = 2;
    public static final int CONNECTING = 1;
    public static final int DISCONNECTED = 10;
    public static final int ERROR = -1;
    public static final int INFO = 20;

    private int code;
    private String message;

    public EngineMessage(int code, String message) {
        this.code=code;
        this.message=message;
    }

    public int getCode() {
        return code;
    }
    public String getMessage() {
        return message;
    }
}