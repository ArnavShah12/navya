package com.navya;

import static com.navya.TestSupport.input;
import static com.navya.TestSupport.signup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.navya.domain.Listing;
import com.navya.exception.ForbiddenException;
import com.navya.exception.InvalidInputException;
import com.navya.exception.ListingNotAvailableException;
import com.navya.exception.PaymentFailedException;
import com.navya.exception.SelfChatException;
import com.navya.service.ListingService;
import com.navya.service.Principal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoostAndReportTest {
    MutableClock clock;
    AppContext ctx;
    Principal anna, ben, admin;
    Listing listing;

    @BeforeEach void setUp() {
        clock = TestSupport.clock();
        // the gateway settles a slow payment after 5 s, and we wait only 300 ms for it
        ctx = TestSupport.context(clock, Duration.ofSeconds(5), 300);
        anna = signup(ctx, "Anna");
        ben = signup(ctx, "Ben");
        var a = ctx.auth.ensureAdmin("admin@example.com", "password123");
        admin = new Principal(a.id, a.role());
        listing = ctx.listings.create(anna, input("Jacket", 500));
    }

    @AfterEach void tearDown() { ctx.close(); }

    private boolean featured() { return ctx.listings.get(null, listing.id()).boostedAt(clock.instant()); }

    @Test
    @DisplayName("TC-43 approved payment pins the listing for the boost period")
    void paid() {
        var r = ctx.boosts.boost(anna, listing.id(), "tok_success", "k1");
        assertThat(r.status()).isEqualTo("paid");
        assertThat(r.order().amountPaise()).isEqualTo(4900); // Wednesday, no discount
        assertThat(featured()).isTrue();
        clock.advance(Duration.ofDays(8));
        assertThat(featured()).isFalse();
        assertThat(ctx.boosts.history(anna)).hasSize(1);
    }

    @Test
    @DisplayName("TC-44 declined payment raises PaymentFailed, marks the order failed, pins nothing")
    void declined() {
        assertThatThrownBy(() -> ctx.boosts.boost(anna, listing.id(), "tok_declined", "k2")).isInstanceOf(PaymentFailedException.class);
        assertThat(featured()).isFalse();
        assertThat(ctx.boosts.history(anna)).extracting(o -> o.order().status()).containsExactly("failed");
        // a new attempt with a new key and a good card works
        assertThat(ctx.boosts.boost(anna, listing.id(), "tok_success", "k3").status()).isEqualTo("paid");
    }

    @Test
    @DisplayName("TC-45 repeating a request with the same key charges once")
    void idempotent() {
        var first = ctx.boosts.boost(anna, listing.id(), "tok_success", "same");
        var again = ctx.boosts.boost(anna, listing.id(), "tok_success", "same");
        assertThat(again.order().id()).isEqualTo(first.order().id());
        assertThat(ctx.boosts.history(anna)).hasSize(1);
        // extended once, not twice
        clock.advance(Duration.ofDays(8));
        assertThat(featured()).isFalse();
    }

    @Test
    @DisplayName("TC-46 a slow bank leaves the order pending, and the background check finishes it later")
    void slow() {
        var r = ctx.boosts.boost(anna, listing.id(), "tok_timeout", "slow");
        assertThat(r.status()).isEqualTo("pending");
        assertThat(featured()).isFalse();
        assertThat(ctx.boosts.reconcile()).isZero(); // bank still undecided
        clock.advance(Duration.ofSeconds(6));
        assertThat(ctx.boosts.reconcile()).isEqualTo(1);
        assertThat(featured()).isTrue();
        assertThat(ctx.boosts.history(anna)).extracting(o -> o.order().status()).containsExactly("paid");
        assertThat(ctx.boosts.reconcile()).isZero(); // nothing left to do, and no second boost
    }

    @Test
    @DisplayName("TC-47 only the seller can boost, and only an available listing")
    void rules() {
        assertThatThrownBy(() -> ctx.boosts.boost(ben, listing.id(), "tok_success", "x1")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> ctx.boosts.boost(anna, listing.id(), "tok_success", " ")).isInstanceOf(InvalidInputException.class);
        ctx.listings.setStatus(anna, listing.id(), "sold");
        assertThatThrownBy(() -> ctx.boosts.boost(anna, listing.id(), "tok_success", "x2")).isInstanceOf(ListingNotAvailableException.class);
    }

    @Test
    @DisplayName("TC-48 a second boost extends from the end of the first")
    void stack() {
        ctx.boosts.boost(anna, listing.id(), "tok_success", "a");
        clock.advance(Duration.ofDays(3));
        ctx.boosts.boost(anna, listing.id(), "tok_success", "b");
        clock.advance(Duration.ofDays(6)); // day 9: first would have ended on day 7, second runs to day 14
        assertThat(featured()).isTrue();
        clock.advance(Duration.ofDays(6)); // day 15
        assertThat(featured()).isFalse();
    }

    @Test
    @DisplayName("TC-49 boosted listings come first in the default order")
    void order() {
        Listing other = ctx.listings.create(anna, input("Newer", 1)); // created after, so newest
        ctx.boosts.boost(anna, listing.id(), "tok_success", "o");
        var items = ctx.listings.search(null, new ListingService.Query(null, null, null, null, null, false, null, 0)).items();
        assertThat(items).extracting(Listing::id).containsExactly(listing.id(), other.id());
    }

    @Test
    @DisplayName("TC-50 ten identical requests at once: one order, one charge")
    void raceSameKey() throws Exception {
        int n = 10;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        for (int i = 0; i < n; i++) pool.submit(() -> {
            try {
                go.await();
                ctx.boosts.boost(anna, listing.id(), "tok_success", "double-click");
                ok.incrementAndGet();
            } catch (Exception e) { /* counted below */ }
        });
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        assertThat(ok.get()).isEqualTo(n);
        assertThat(ctx.boosts.history(anna)).hasSize(1);
        clock.advance(Duration.ofDays(8));
        assertThat(featured()).isFalse(); // a double boost would still be running
    }

    @Test
    @DisplayName("TC-68 a pending order the gateway has never heard of (for example after a restart) fails after 10 minutes and pins nothing")
    void orphanOrder() {
        ctx.db.update("INSERT INTO orders (id, user_id, listing_id, amount_paise, idempotency_key, created_at) VALUES (?,?,?,?,?,?)",
                com.navya.util.Ids.newId(), anna.userId(), listing.id(), 4900, "orphan", clock.instant());
        clock.advance(Duration.ofMinutes(5));
        assertThat(ctx.boosts.reconcile()).isZero(); // too early to give up
        clock.advance(Duration.ofMinutes(6));
        assertThat(ctx.boosts.reconcile()).isEqualTo(1);
        assertThat(ctx.boosts.history(anna)).extracting(o -> o.order().status()).containsExactly("failed");
        assertThat(featured()).isFalse();
    }

    // ---- reports ----

    @Test
    @DisplayName("TC-51 report, duplicate report ignored, own listing refused")
    void report() {
        ctx.reports.report(ben, listing.id(), "Looks fake");
        ctx.reports.report(ben, listing.id(), "Looks fake again");
        assertThat(ctx.reports.open(admin)).hasSize(1);
        assertThatThrownBy(() -> ctx.reports.report(anna, listing.id(), "mine")).isInstanceOf(SelfChatException.class);
        assertThatThrownBy(() -> ctx.reports.report(ben, listing.id(), " ")).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("TC-52 only the Navya team sees reports; 'remove' takes the listing down, 'dismiss' keeps it")
    void resolve() {
        ctx.reports.report(ben, listing.id(), "Prohibited item");
        assertThatThrownBy(() -> ctx.reports.open(ben)).isInstanceOf(ForbiddenException.class);
        String rid = ctx.reports.open(admin).get(0).report().id();
        assertThatThrownBy(() -> ctx.reports.resolve(ben, rid, "remove")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> ctx.reports.resolve(admin, rid, "explode")).isInstanceOf(InvalidInputException.class);
        ctx.reports.resolve(admin, rid, "remove");
        assertThat(ctx.reports.open(admin)).isEmpty();
        assertThat(ctx.listings.search(null, new ListingService.Query("jacket", null, null, null, null, false, null, 0)).total()).isZero();
        assertThat(ctx.listings.get(anna, listing.id()).status()).isEqualTo("removed"); // seller can still see why
        assertThat(ctx.audit.tail(10)).anyMatch(l -> l.contains("report.remove"));

        Listing second = ctx.listings.create(anna, input("Second", 1));
        ctx.reports.report(ben, second.id(), "meh");
        ctx.reports.resolve(admin, ctx.reports.open(admin).get(0).report().id(), "dismiss");
        assertThat(ctx.listings.get(null, second.id()).status()).isEqualTo("available");
    }

    @Test
    @DisplayName("TC-53 export writes CSV and JSON files, and spreadsheet formulas are defused")
    void export() {
        ctx.listings.create(anna, new ListingService.Input("=HYPERLINK(\"http://evil\")", 1L, "Shoes", "Good", "", "Pune", "d", List.of()));
        String csv = ctx.exports.csv();
        assertThat(csv).startsWith("id,title,price_rupees").contains("\"'=HYPERLINK(\"\"http://evil\"\")\"");
        String key = ctx.exports.exportToStorage();
        assertThat(ctx.storage.exists(key)).isTrue();
        assertThat(ctx.storage.exists(key.replace(".csv", ".json"))).isTrue();
    }
}
