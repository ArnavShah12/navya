package com.navya.exception;

public class RateLimitedException extends NavyaException {
    public RateLimitedException(String message) { super(429, "RATE_LIMITED", message); }
}
