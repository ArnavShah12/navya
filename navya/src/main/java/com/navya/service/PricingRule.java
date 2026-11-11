package com.navya.service;

import java.time.Instant;

/** One step in working out what a boost costs. Rules run in order, each gets the amount from the one before. */
public interface PricingRule {
    long adjust(long amountPaise, Instant now);
}
