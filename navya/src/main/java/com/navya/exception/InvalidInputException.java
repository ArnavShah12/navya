package com.navya.exception;

public class InvalidInputException extends NavyaException {
    public InvalidInputException(String message) { super(400, "INVALID_INPUT", message); }
}
