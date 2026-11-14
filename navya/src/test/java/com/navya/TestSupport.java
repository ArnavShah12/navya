package com.navya;

import com.navya.config.AppConfig;
import com.navya.domain.Member;
import com.navya.service.ListingService;
import com.navya.service.Principal;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.imageio.ImageIO;

/** One PostgreSQL per test run, emptied before every test. */
public final class TestSupport {
    private static EmbeddedPostgres pg;

    private TestSupport() {}

    private static synchronized EmbeddedPostgres pg() {
        if (pg == null) {
            try {
                pg = EmbeddedPostgres.start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        pg.close();
                    } catch (IOException e) { /* test JVM is ending */ }
                }));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return pg;
    }

    public static String jdbcUrl() { return pg().getJdbcUrl("postgres", "postgres"); }

    /** Empties every table so each test starts clean. Tables are created by the app's own migration. */
    public static void wipe() {
        try (Connection c = pg().getPostgresDatabase().getConnection(); Statement st = c.createStatement()) {
            st.execute("DO $$ BEGIN IF to_regclass('public.users') IS NOT NULL THEN TRUNCATE users, listings, listing_images, favourites, conversations, messages, reports, orders RESTART IDENTITY CASCADE; END IF; END $$");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static AppContext context(MutableClock clock) { return context(clock, Duration.ofSeconds(5), 3_000); }

    public static AppContext context(MutableClock clock, Duration settle, long chargeTimeoutMillis) {
        wipe();
        try {
            Path dir = Files.createTempDirectory("navya-test");
            return AppContext.create(AppConfig.forTest(dir, jdbcUrl(), "postgres", "postgres"), clock, settle, chargeTimeoutMillis);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A second app instance on the same database, to prove two instances behave. */
    public static AppContext secondInstance(AppContext first, MutableClock clock) {
        try {
            Path dir = Files.createTempDirectory("navya-test2");
            AppConfig f = first.config;
            // same signing secret as the first instance, as in a real deployment
            AppConfig cfg = new AppConfig(0, dir, f.jwtSecret(), true, f.dbUrl(), f.dbUser(), f.dbPassword(), "local", null, "ap-south-1", null, 4900, 7, 60);
            return AppContext.create(cfg, clock, Duration.ofSeconds(5), 3_000);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static MutableClock clock() { return new MutableClock(Instant.parse("2026-11-25T06:00:00Z")); } // a Wednesday

    public static Principal signup(AppContext ctx, String name) {
        Member m = ctx.auth.signup(name.toLowerCase() + "@example.com", name, "Pune", "password123");
        return new Principal(m.id, m.role());
    }

    public static ListingService.Input input(String title, long price) {
        return new ListingService.Input(title, price, "Clothes", "Good", "M", "Pune", "A test item", List.of());
    }

    public static byte[] jpeg() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
