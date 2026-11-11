package com.navya.service;

import com.navya.domain.Listing;
import com.navya.domain.Order;
import com.navya.exception.ForbiddenException;
import com.navya.exception.InvalidInputException;
import com.navya.exception.ListingNotAvailableException;
import com.navya.exception.NotFoundException;
import com.navya.exception.PaymentFailedException;
import com.navya.payment.PaymentGateway;
import com.navya.repo.ListingRepo;
import com.navya.repo.OrderRepo;
import com.navya.util.Ids;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sellers pay to pin a listing at the top for a few days. This is the revenue stream.
 * The charge runs on a thread pool with a timeout. If the gateway is slow the order stays pending and
 * reconcile() finishes it later. The idempotency key makes a repeated request return the first order.
 */
public final class BoostService {
    private static final Logger log = LoggerFactory.getLogger(BoostService.class);

    /** status is paid, failed or pending (the gateway has not answered yet). */
    public record Result(Order order, String status, String message) {}

    private final OrderRepo orders;
    private final ListingRepo listings;
    private final PricingService pricing;
    private final PaymentGateway gateway;
    private final ExecutorService pool;
    private final long timeoutMillis;
    private final int boostDays;
    private final Clock clock;
    private final Metrics metrics;
    private final AuditLog audit;

    public BoostService(OrderRepo orders, ListingRepo listings, PricingService pricing, PaymentGateway gateway, ExecutorService pool,
                        long timeoutMillis, int boostDays, Clock clock, Metrics metrics, AuditLog audit) {
        this.orders = orders;
        this.listings = listings;
        this.pricing = pricing;
        this.gateway = gateway;
        this.pool = pool;
        this.timeoutMillis = timeoutMillis;
        this.boostDays = boostDays;
        this.clock = clock;
        this.metrics = metrics;
        this.audit = audit;
    }

    public long quote() { return pricing.boostPrice(clock.instant()); }

    public Result boost(Principal p, String listingId, String token, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 80) throw new InvalidInputException("A request key is required");
        Order existing = orders.byKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.userId().equals(p.userId())) throw new ForbiddenException("Request key already used");
            return describe(existing);
        }
        Listing l = Ids.valid(listingId) ? listings.get(listingId).orElse(null) : null;
        if (l == null) throw new NotFoundException("Listing not found");
        if (!l.sellerId().equals(p.userId())) throw new ForbiddenException("This is not your listing");
        if (!Listing.AVAILABLE.equals(l.status())) throw new ListingNotAvailableException("Only available listings can be boosted");

        Order o = new Order(Ids.newId(), p.userId(), listingId, quote(), Order.PENDING, idempotencyKey, clock.instant());
        if (!orders.insertPending(o)) return describe(orders.byKey(idempotencyKey).orElseThrow()); // lost a race with the same key

        PaymentGateway.ChargeResult r;
        Future<PaymentGateway.ChargeResult> f = pool.submit(() -> gateway.charge(new PaymentGateway.ChargeRequest(o.id(), o.amountPaise(), token)));
        try {
            r = f.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            log.warn("payment slow order={}", o.id());
            return new Result(o, Order.PENDING, "Payment is taking longer than usual. Your boost starts when it clears.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(o, Order.PENDING, "Payment is still processing");
        } catch (ExecutionException e) {
            log.error("payment error order={}", o.id(), e.getCause());
            return new Result(o, Order.PENDING, "Payment is still processing");
        }
        return apply(o, r);
    }

    private Result apply(Order o, PaymentGateway.ChargeResult r) {
        switch (r.status()) {
            case SUCCESS -> {
                if (orders.markPaid(o, clock.instant(), boostDays)) {
                    metrics.boostsPaid.incrementAndGet();
                    audit.record(o.userId(), "boost.paid", o.id());
                }
                return describe(orders.byId(o.id()).orElseThrow());
            }
            case DECLINED -> {
                if (orders.markFailed(o.id())) metrics.paymentsFailed.incrementAndGet();
                throw new PaymentFailedException(r.message());
            }
            default -> {
                return new Result(o, Order.PENDING, "Payment is still processing");
            }
        }
    }

    private Result describe(Order o) {
        String msg = switch (o.status()) {
            case Order.PAID -> "Boost active";
            case Order.FAILED -> "Payment failed";
            default -> "Payment is still processing";
        };
        return new Result(o, o.status(), msg);
    }

    /** Finishes orders left pending by a slow gateway. Run by the background thread. Safe to run on every instance. */
    public int reconcile() {
        int done = 0;
        for (Order o : orders.pendingBefore(clock.instant().minus(Duration.ofSeconds(1)))) {
            try {
                PaymentGateway.ChargeResult r = gateway.status(o.id());
                if (r.status() != PaymentGateway.Status.PENDING) {
                    try {
                        apply(o, r);
                    } catch (PaymentFailedException e) {
                        // already marked failed
                    }
                    done++;
                }
            } catch (NotFoundException e) {
                // the gateway never saw this order (for example after a restart): the seller was not charged
                if (o.createdAt().isBefore(clock.instant().minus(Duration.ofMinutes(10))) && orders.markFailed(o.id())) done++;
            }
        }
        return done;
    }

    public List<OrderRepo.Row> history(Principal p) { return orders.byUser(p.userId()); }
}
