package com.navya.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Counters shown on /metrics and used for CloudWatch metric filters on the log. Thread-safe. */
public final class Metrics {
    public final AtomicLong requests = new AtomicLong();
    public final AtomicLong serverErrors = new AtomicLong();
    public final AtomicLong signups = new AtomicLong();
    public final AtomicLong listingsCreated = new AtomicLong();
    public final AtomicLong listingsExpired = new AtomicLong();
    public final AtomicLong messagesSent = new AtomicLong();
    public final AtomicLong boostsPaid = new AtomicLong();
    public final AtomicLong paymentsFailed = new AtomicLong();

    public Map<String, Long> snapshot() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("requests", requests.get());
        m.put("serverErrors", serverErrors.get());
        m.put("signups", signups.get());
        m.put("listingsCreated", listingsCreated.get());
        m.put("listingsExpired", listingsExpired.get());
        m.put("messagesSent", messagesSent.get());
        m.put("boostsPaid", boostsPaid.get());
        m.put("paymentsFailed", paymentsFailed.get());
        return m;
    }
}
