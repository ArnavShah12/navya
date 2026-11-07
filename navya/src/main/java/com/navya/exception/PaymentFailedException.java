package com.navya.exception;

public class PaymentFailedException extends NavyaException {
    public PaymentFailedException(String message) { super(402, "PAYMENT_FAILED", message); }
}
