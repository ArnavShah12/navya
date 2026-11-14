package com.navya;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.navya.exception.NotFoundException;
import com.navya.payment.MockGateway;
import com.navya.payment.PaymentGateway;
import com.navya.service.PricingService;
import com.navya.service.RateLimiter;
import com.navya.service.WeekendDiscountRule;
import com.navya.service.WholeRupeeRule;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PricingAndGatewayTest {
    private final PricingService pricing = new PricingService(4900, List.of(new WeekendDiscountRule(20), new WholeRupeeRule()));

    @Test
    @DisplayName("TC-07 boost costs the full fee on a weekday")
    void weekday() { assertThat(pricing.boostPrice(Instant.parse("2026-11-25T06:00:00Z"))).isEqualTo(4900); } // Wednesday

    @Test
    @DisplayName("TC-08 boost is 20% off on Saturday (Indian time) and rounds to a whole rupee")
    void weekend() {
        assertThat(pricing.boostPrice(Instant.parse("2026-11-28T06:00:00Z"))).isEqualTo(3900); // 4900 - 980 = 3920 -> 3900
        // Friday 20:00 UTC is already Saturday 01:30 in India
        assertThat(pricing.boostPrice(Instant.parse("2026-11-27T20:00:00Z"))).isEqualTo(3900);
        // Sunday 20:00 UTC is Monday 01:30 in India: no discount
        assertThat(pricing.boostPrice(Instant.parse("2026-11-29T20:00:00Z"))).isEqualTo(4900);
    }

    @Test
    @DisplayName("Price never goes below zero")
    void floor() { assertThat(new PricingService(100, List.of((a, n) -> a - 500)).boostPrice(Instant.now())).isZero(); }

    @Test
    @DisplayName("TC-09 mock gateway: each token gives its outcome")
    void tokens() {
        MockGateway g = new MockGateway(TestSupport.clock(), Duration.ofSeconds(5));
        assertThat(g.charge(new PaymentGateway.ChargeRequest("a", 100, "tok_success")).status()).isEqualTo(PaymentGateway.Status.SUCCESS);
        assertThat(g.charge(new PaymentGateway.ChargeRequest("b", 100, "tok_declined")).status()).isEqualTo(PaymentGateway.Status.DECLINED);
        assertThat(g.charge(new PaymentGateway.ChargeRequest("c", 100, "tok_timeout")).status()).isEqualTo(PaymentGateway.Status.PENDING);
        assertThat(g.charge(new PaymentGateway.ChargeRequest("d", 100, "whatever")).status()).isEqualTo(PaymentGateway.Status.DECLINED);
        assertThat(g.charge(new PaymentGateway.ChargeRequest("e", 100, null)).status()).isEqualTo(PaymentGateway.Status.DECLINED);
    }

    @Test
    @DisplayName("Mock gateway: same reference is never charged twice, slow payment settles later")
    void idempotentAndSlow() {
        MutableClock clock = TestSupport.clock();
        MockGateway g = new MockGateway(clock, Duration.ofSeconds(5));
        g.charge(new PaymentGateway.ChargeRequest("x", 100, "tok_timeout"));
        assertThat(g.status("x").status()).isEqualTo(PaymentGateway.Status.PENDING);
        clock.advance(Duration.ofSeconds(6));
        assertThat(g.status("x").status()).isEqualTo(PaymentGateway.Status.SUCCESS);
        // a second charge with a different token still returns the first result
        assertThat(g.charge(new PaymentGateway.ChargeRequest("x", 100, "tok_declined")).status()).isEqualTo(PaymentGateway.Status.SUCCESS);
        assertThatThrownBy(() -> g.status("unknown")).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("TC-10 rate limiter allows the burst, blocks the next call, refills over time")
    void rateLimiter() {
        MutableClock clock = TestSupport.clock();
        RateLimiter r = new RateLimiter(3, 60, clock); // 3 burst, 1 token a second
        assertThat(r.tryAcquire("k")).isTrue();
        assertThat(r.tryAcquire("k")).isTrue();
        assertThat(r.tryAcquire("k")).isTrue();
        assertThat(r.tryAcquire("k")).isFalse();
        assertThat(r.tryAcquire("other")).isTrue(); // keys are separate
        clock.advance(Duration.ofSeconds(2));
        assertThat(r.tryAcquire("k")).isTrue();
    }
}
