package com.learnium.learniumbackend.exception;

public class ValidationException extends RuntimeException {
    public ValidationException(String message) { super(message); }
}