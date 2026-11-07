package com.navya.exception;

public class UnauthorizedException extends NavyaException {
    public UnauthorizedException(String message) { super(401, "UNAUTHORIZED", message); }
}
