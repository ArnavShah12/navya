package com.navya.domain;

import java.time.Instant;

public record Report(String id, String listingId, String reporterId, String reason, String status, Instant createdAt) {}
