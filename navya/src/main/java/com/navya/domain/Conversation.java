package com.navya.domain;

import java.time.Instant;

public record Conversation(String id, String listingId, String buyerId, String sellerId, Instant createdAt) {
    public String otherOf(String userId) { return buyerId.equals(userId) ? sellerId : buyerId; }
    public boolean has(String userId) { return buyerId.equals(userId) || sellerId.equals(userId); }
}
