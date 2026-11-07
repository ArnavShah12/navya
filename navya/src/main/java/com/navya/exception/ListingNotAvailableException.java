package com.navya.exception;

public class ListingNotAvailableException extends NavyaException {
    public ListingNotAvailableException(String message) { super(409, "LISTING_NOT_AVAILABLE", message); }
}
