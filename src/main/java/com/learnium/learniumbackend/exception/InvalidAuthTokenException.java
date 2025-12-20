package com.learnium.learniumbackend.exception;

public class InvalidAuthTokenException extends RuntimeException {
    public InvalidAuthTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
