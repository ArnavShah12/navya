package com.navya.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;

/** Percent off on Saturday and Sunday in Indian time, when people list the most. */
public final class WeekendDiscountRule implements PricingRule {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private final int percentOff;

    public WeekendDiscountRule(int percentOff) { this.percentOff = percentOff; }

    @Override
    public long adjust(long amountPaise, Instant now) {
        DayOfWeek d = now.atZone(IST).getDayOfWeek();
        if (d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY) return amountPaise;
        return amountPaise - amountPaise * percentOff / 100;
    }
}
