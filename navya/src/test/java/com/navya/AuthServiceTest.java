package com.navya;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.navya.domain.Role;
import com.navya.exception.DuplicateEmailException;
import com.navya.exception.InvalidInputException;
import com.navya.exception.UnauthorizedException;
import com.navya.service.Principal;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    MutableClock clock;
    AppContext ctx;

    @BeforeEach void setUp() {
        clock = TestSupport.clock();
        ctx = TestSupport.context(clock);
    }

    @AfterEach void tearDown() { ctx.close(); }

    @Test
    @DisplayName("TC-14 sign up, then log in, then the token identifies the member")
    void signupLogin() {
        var m = ctx.auth.signup("Aanya@Example.com", "Aanya", "Pune", "password123");
        assertThat(m.email).isEqualTo("aanya@example.com");
        assertThat(m.passwordHash).isNotEqualTo("password123").startsWith("$2");
        var r = ctx.auth.login("aanya@example.com", "password123");
        Principal p = ctx.auth.verify(r.token());
        assertThat(p.userId()).isEqualTo(m.id);
        assertThat(p.role()).isEqualTo(Role.MEMBER);
    }

    @Test
    @DisplayName("TC-15 the same email cannot sign up twice, whatever its case")
    void duplicate() {
        ctx.auth.signup("a@example.com", "A", "Pune", "password123");
        assertThatThrownBy(() -> ctx.auth.signup("A@EXAMPLE.COM", "B", "Pune", "password123")).isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("TC-16 wrong password and unknown email give the same error")
    void sameError() {
        ctx.auth.signup("a@example.com", "A", "Pune", "password123");
        Throwable wrong = org.assertj.core.api.Assertions.catchThrowable(() -> ctx.auth.login("a@example.com", "nope-nope"));
        Throwable unknown = org.assertj.core.api.Assertions.catchThrowable(() -> ctx.auth.login("x@example.com", "password123"));
        assertThat(wrong).isInstanceOf(UnauthorizedException.class);
        assertThat(unknown).isInstanceOf(UnauthorizedException.class).hasMessage(wrong.getMessage());
    }

    @Test
    @DisplayName("TC-17 sign up rejects short password, bad email and empty name")
    void invalid() {
        assertThatThrownBy(() -> ctx.auth.signup("a@example.com", "A", "Pune", "short")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> ctx.auth.signup("not-an-email", "A", "Pune", "password123")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> ctx.auth.signup("a@example.com", " ", "Pune", "password123")).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("TC-18 tampered, garbage and expired tokens are refused")
    void tokens() {
        var m = ctx.auth.signup("a@example.com", "A", "Pune", "password123");
        String t = ctx.auth.token(m);
        String[] parts = t.split("\\.");
        // swap the payload for an admin claim, keep the old signature
        String forged = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"navya\",\"sub\":\"" + m.id + "\",\"role\":\"ADMIN\",\"exp\":9999999999}").getBytes()) + "." + parts[2];
        assertThatThrownBy(() -> ctx.auth.verify(forged)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> ctx.auth.verify("garbage")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> ctx.auth.verify(null)).isInstanceOf(UnauthorizedException.class);
        assertThat(ctx.auth.verify(t).userId()).isEqualTo(m.id);
        clock.advance(Duration.ofHours(13));
        assertThatThrownBy(() -> ctx.auth.verify(t)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("TC-19 ensureAdmin creates one admin and is safe to repeat")
    void admin() {
        var a = ctx.auth.ensureAdmin("admin@example.com", "password123");
        assertThat(a.role()).isEqualTo(Role.ADMIN);
        assertThat(ctx.auth.ensureAdmin("admin@example.com", "different-pass")).isNotNull();
        assertThat(ctx.auth.login("admin@example.com", "password123").user().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("TC-20 two simultaneous sign-ups with one email: exactly one wins")
    void raceSignup() throws Exception {
        int n = 12;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        CopyOnWriteArrayList<Object> results = new CopyOnWriteArrayList<>();
        for (int i = 0; i < n; i++) pool.submit(() -> {
            try {
                go.await();
                ctx.auth.signup("race@example.com", "R", "Pune", "password123");
                results.add("ok");
            } catch (DuplicateEmailException e) {
                results.add("dup");
            } catch (Exception e) {
                results.add("other " + e);
            }
        });
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(results).filteredOn("ok"::equals).hasSize(1);
        assertThat(results).filteredOn("dup"::equals).hasSize(n - 1);
    }
}
