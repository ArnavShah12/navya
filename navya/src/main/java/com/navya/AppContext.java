package com.navya;

import com.navya.config.AppConfig;
import com.navya.payment.MockGateway;
import com.navya.repo.ChatRepo;
import com.navya.repo.Db;
import com.navya.repo.ListingRepo;
import com.navya.repo.OrderRepo;
import com.navya.repo.ReportRepo;
import com.navya.repo.UserRepo;
import com.navya.service.AuditLog;
import com.navya.service.AuthService;
import com.navya.service.BoostService;
import com.navya.service.ChatService;
import com.navya.service.ExportService;
import com.navya.service.ListingService;
import com.navya.service.Metrics;
import com.navya.service.PricingRule;
import com.navya.service.PricingService;
import com.navya.service.RateLimiter;
import com.navya.service.ReportService;
import com.navya.service.WeekendDiscountRule;
import com.navya.service.WholeRupeeRule;
import com.navya.storage.FileStorage;
import com.navya.storage.LocalFileStorage;
import com.navya.storage.S3FileStorage;
import com.navya.worker.ChatHub;
import com.navya.worker.Notifier;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds every object once and owns the threads:
 * navya-reaper (every 30 s: expire old listings, finish slow payments),
 * navya-pay-N (payment calls with a timeout), navya-notifier (outgoing notices),
 * navya-chat-listener (hears NOTIFY from PostgreSQL and pings WebSocket sessions).
 */
public final class AppContext implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AppContext.class);

    public final AppConfig config;
    public final Clock clock;
    public final Db db;
    public final Metrics metrics = new Metrics();
    public final AuditLog audit;
    public final Notifier notifier;
    public final FileStorage storage;
    public final MockGateway gateway;
    public final AuthService auth;
    public final ListingService listings;
    public final ChatService chats;
    public final ReportService reports;
    public final BoostService boosts;
    public final ExportService exports;
    public final ChatHub hub;
    public final RateLimiter loginLimiter = new RateLimiter(5, 5, Clock.systemUTC());
    public final RateLimiter signupLimiter = new RateLimiter(10, 10, Clock.systemUTC());
    public final RateLimiter writeLimiter = new RateLimiter(30, 30, Clock.systemUTC());
    public final RateLimiter uploadLimiter = new RateLimiter(20, 20, Clock.systemUTC());
    public final RateLimiter messageLimiter = new RateLimiter(30, 30, Clock.systemUTC());
    public final RateLimiter payLimiter = new RateLimiter(10, 10, Clock.systemUTC());
    private final ExecutorService paymentPool;
    private final ScheduledExecutorService scheduler;

    private AppContext(AppConfig cfg, Clock clock, Duration gatewaySettleDelay, long chargeTimeoutMillis) {
        this.config = cfg;
        this.clock = clock;
        this.db = Db.open(cfg.dbUrl(), cfg.dbUser(), cfg.dbPassword(), 10);
        this.db.migrate();
        this.audit = new AuditLog(cfg.dataDir(), clock);
        this.notifier = new Notifier(cfg.dataDir(), clock, 1_000);
        this.storage = cfg.storageType().equals("s3")
                ? S3FileStorage.create(cfg.s3Bucket(), cfg.s3Region(), cfg.s3Endpoint())
                : new LocalFileStorage(cfg.dataDir().resolve("files"));
        this.gateway = new MockGateway(clock, gatewaySettleDelay);
        UserRepo users = new UserRepo(db);
        ListingRepo listingRepo = new ListingRepo(db);
        this.auth = new AuthService(users, cfg.jwtSecret(), clock);
        this.listings = new ListingService(listingRepo, storage, audit, clock, metrics);
        this.chats = new ChatService(new ChatRepo(db), listingRepo, notifier, metrics);
        this.reports = new ReportService(new ReportRepo(db), listingRepo, audit, notifier, clock);
        List<PricingRule> rules = List.of(new WeekendDiscountRule(20), new WholeRupeeRule());
        this.paymentPool = Executors.newFixedThreadPool(8, named("navya-pay"));
        this.boosts = new BoostService(new OrderRepo(db, listingRepo), listingRepo, new PricingService(cfg.boostFeePaise(), rules), gateway,
                paymentPool, chargeTimeoutMillis, cfg.boostDays(), clock, metrics, audit);
        this.exports = new ExportService(listingRepo, storage, clock);
        this.hub = new ChatHub(db.dataSource());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(named("navya-reaper"));
    }

    public static AppContext create(AppConfig cfg, Clock clock) {
        return new AppContext(cfg, clock, Duration.ofSeconds(5), 3_000);
    }

    /** For tests: control how long the mock gateway takes to settle a slow payment and how long we wait for it. */
    public static AppContext create(AppConfig cfg, Clock clock, Duration gatewaySettleDelay, long chargeTimeoutMillis) {
        return new AppContext(cfg, clock, gatewaySettleDelay, chargeTimeoutMillis);
    }

    /** Start the chat listener and the periodic housekeeping thread. */
    public void startBackground() {
        hub.start();
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                int expired = listings.expireOld(config.listingLifeDays());
                int settled = boosts.reconcile();
                if (expired > 0 || settled > 0) log.info("housekeeping expired={} payments_settled={}", expired, settled);
            } catch (RuntimeException e) {
                log.error("Background housekeeping failed", e);
            }
        }, 1, 30, TimeUnit.SECONDS);
    }

    /** Orderly shutdown: stop housekeeping, finish payments, drain notices, close the pool. */
    @Override
    public void close() {
        scheduler.shutdown();
        paymentPool.shutdown();
        try {
            scheduler.awaitTermination(3, TimeUnit.SECONDS);
            paymentPool.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        hub.close();
        notifier.close();
        db.close();
    }

    public boolean healthy() { return db.ping(); }

    private static ThreadFactory named(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
