package com.navya.domain;

import java.time.Instant;
import java.util.List;

/** One item for sale. Price is in paise so money never goes through floating point. */
public record Listing(String id, String sellerId, String title, long pricePaise, String category, String condition,
                      String size, String city, String description, List<String> images, String status,
                      Instant boostedUntil, Instant createdAt) {

    public static final String AVAILABLE = "available";
    public static final String SOLD = "sold";
    public static final String REMOVED = "removed";
    public static final String EXPIRED = "expired";

    public boolean boostedAt(Instant now) { return boostedUntil != null && boostedUntil.isAfter(now); }
}
