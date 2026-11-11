package com.navya.payment;

/**
 * Boundary to a payment provider. The reference is our own order id, so calling charge twice for
 * the same order never charges twice. Test mode only in this project.
 */
public interface PaymentGateway {

    enum Status { SUCCESS, DECLINED, PENDING }

    record ChargeRequest(String reference, long amountPaise, String token) {}

    record ChargeResult(Status status, String reference, String message) {}

    ChargeResult charge(ChargeRequest request);

    /** Ask the provider what happened to an earlier charge. Used when a charge timed out. */
    ChargeResult status(String reference);
}
