package com.navya.exception;

public class TooManyImagesException extends NavyaException {
    public TooManyImagesException(String message) { super(422, "TOO_MANY_IMAGES", message); }
}
