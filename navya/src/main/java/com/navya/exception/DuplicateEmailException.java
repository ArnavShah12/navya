package com.navya.exception;

public class DuplicateEmailException extends NavyaException {
    public DuplicateEmailException(String message) { super(409, "DUPLICATE_EMAIL", message); }
}
