package com.navya.exception;

public class PersistenceException extends NavyaException {
    public PersistenceException(String message, Throwable cause) { super(500, "PERSISTENCE_ERROR", message, cause); }
    public PersistenceException(String message) { super(500, "PERSISTENCE_ERROR", message); }
}
