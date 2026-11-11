package com.navya.payment;

import com.navya.exception.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for a real gateway. The token picks the outcome:
 * tok_success approves, tok_declined declines, tok_timeout answers "pending" and approves a few
 * seconds later, which exercises the reconciliation path. Anything else is declined.
 * Results are stored by reference, so a repeated charge returns the first result.
 * shortcut: results live in memory, so a restart forgets them. A real gateway keeps its own records.
 */
public final class MockGateway implements PaymentGateway {
    public static final String SUCCESS = "tok_success";
    public static final String DECLINED = "tok_declined";
    public static final String TIMEOUT = "tok_timeout";

    private record Entry(ChargeResult first, Instant settleAt) {}

    private final ConcurrentHashMap<String, Entry> payments = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration settleDelay;

    public MockGateway(Clock clock, Duration settleDelay) {
        this.clock = clock;
        this.settleDelay = settleDelay;
    }

    @Override
    public ChargeResult charge(ChargeRequest r) {
        Entry e = payments.computeIfAbsent(r.reference(), ref -> switch (String.valueOf(r.token())) {
            case SUCCESS -> new Entry(new ChargeResult(Status.SUCCESS, ref, "Approved (test mode)"), null);
            case TIMEOUT -> new Entry(new ChargeResult(Status.PENDING, ref, "Gateway did not answer in time (test mode)"), clock.instant().plus(settleDelay));
            case DECLINED -> new Entry(new ChargeResult(Status.DECLINED, ref, "Card declined (test mode)"), null);
            default -> new Entry(new ChargeResult(Status.DECLINED, ref, "Unknown test token"), null);
        });
        return resolve(e);
    }

    @Override
    public ChargeResult status(String reference) {
        Entry e = payments.get(reference);
        if (e == null) throw new NotFoundException("No payment for reference " + reference);
        return resolve(e);
    }

    private ChargeResult resolve(Entry e) {
        if (e.settleAt() != null && !clock.instant().isBefore(e.settleAt())) {
            return new ChargeResult(Status.SUCCESS, e.first().reference(), "Approved after delay (test mode)");
        }
        return e.first();
    }
}
