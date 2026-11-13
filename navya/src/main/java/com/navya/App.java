package com.navya;

import com.navya.config.AppConfig;
import com.navya.web.Api;
import io.javalin.Javalin;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    private App() {}

    public static void main(String[] args) {
        AppConfig cfg = AppConfig.fromEnv(System.getenv());
        AppContext ctx = AppContext.create(cfg, Clock.systemUTC());
        // ADMIN_EMAIL + ADMIN_PASSWORD create the moderator account once. SEED=true adds demo members and listings.
        String adminEmail = System.getenv("ADMIN_EMAIL"), adminPassword = System.getenv("ADMIN_PASSWORD");
        if (adminEmail != null && !adminEmail.isBlank() && adminPassword != null && !adminPassword.isBlank()) ctx.auth.ensureAdmin(adminEmail, adminPassword);
        if ("true".equalsIgnoreCase(System.getenv("SEED"))) Seeder.run(ctx, System.getenv("SEED_PASSWORD"));
        ctx.startBackground();
        Javalin app = Api.create(ctx);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down");
            app.stop();
            ctx.close();
        }, "navya-shutdown"));
        app.start(cfg.port());
        log.info("Navya is running on port {} (dev mode: {}, storage: {})", cfg.port(), cfg.dev(), cfg.storageType());
    }
}
