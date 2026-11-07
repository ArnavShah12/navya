package com.navya.domain;

import java.time.Instant;

public record Message(long id, String conversationId, String senderId, String body, Instant createdAt) {}
