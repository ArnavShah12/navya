package com.navya.service;

import java.time.Instant;

/** Rounds to the nearest whole rupee so no one is charged a part-rupee. */
public final class WholeRupeeRule implements PricingRule {
    @Override
    public long adjust(long amountPaise, Instant now) { return (amountPaise + 50) / 100 * 100; }
}
