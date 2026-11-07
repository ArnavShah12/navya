package com.navya.exception;

/** Base class for every error the app raises on purpose. Carries an HTTP status and a stable error code. */
public class NavyaException extends RuntimeException {
    private final int status;
    private final String code;

    public NavyaException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public NavyaException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
