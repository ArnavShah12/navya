package com.navya.domain;

import java.time.Instant;

/** A paid boost. Status moves pending to paid or failed, never back. */
public record Order(String id, String userId, String listingId, long amountPaise, String status, String idempotencyKey, Instant createdAt) {
    public static final String PENDING = "pending";
    public static final String PAID = "paid";
    public static final String FAILED = "failed";
}
