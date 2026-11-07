package com.navya.domain;

import com.navya.exception.InvalidInputException;
import java.util.Locale;

/** Amounts are whole paise (1 rupee = 100 paise). The API speaks rupees, the database speaks paise. */
public final class Money {
    public static final long MAX_RUPEES = 10_000_000L;

    private Money() {}

    public static long toPaise(long rupees) {
        if (rupees < 0 || rupees > MAX_RUPEES) throw new InvalidInputException("Price must be between 0 and " + MAX_RUPEES + " rupees");
        return rupees * 100;
    }

    /** "Rs 1,250" or "Rs 1,250.50". */
    public static String format(long paise) {
        long r = paise / 100, p = paise % 100;
        String whole = String.format(Locale.ROOT, "%,d", r);
        return p == 0 ? "Rs " + whole : String.format(Locale.ROOT, "Rs %s.%02d", whole, p);
    }
}
