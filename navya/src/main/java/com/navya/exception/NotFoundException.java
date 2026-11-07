package com.navya.exception;

public class NotFoundException extends NavyaException {
    public NotFoundException(String message) { super(404, "NOT_FOUND", message); }
}
