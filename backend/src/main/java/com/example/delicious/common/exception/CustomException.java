package com.example.delicious.common.exception;

public class CustomException extends RuntimeException {
    private final String code;
    private final String msg;

    public CustomException(String code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    public String getCode() { return code; }
    public String getMsg() { return msg; }
}