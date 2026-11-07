package com.navya.exception;

public class ForbiddenException extends NavyaException {
    public ForbiddenException(String message) { super(403, "FORBIDDEN", message); }
}
