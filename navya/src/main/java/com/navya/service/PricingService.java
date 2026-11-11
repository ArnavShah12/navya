package com.navya.service;

import java.time.Instant;
import java.util.List;

/** What a boost costs right now: the base fee passed through each pricing rule. */
public final class PricingService {
    private final long baseFeePaise;
    private final List<PricingRule> rules;

    public PricingService(long baseFeePaise, List<PricingRule> rules) {
        this.baseFeePaise = baseFeePaise;
        this.rules = rules;
    }

    public long boostPrice(Instant now) {
        long amount = baseFeePaise;
        for (PricingRule r : rules) amount = r.adjust(amount, now);
        return Math.max(0, amount);
    }
}
