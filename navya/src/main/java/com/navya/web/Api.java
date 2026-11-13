package com.navya.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.navya.AppContext;
import com.navya.domain.Listing;
import com.navya.domain.User;
import com.navya.exception.InvalidInputException;
import com.navya.exception.NavyaException;
import com.navya.exception.NotFoundException;
import com.navya.exception.UnauthorizedException;
import com.navya.repo.ChatRepo;
import com.navya.repo.ListingRepo;
import com.navya.service.BoostService;
import com.navya.service.ListingService;
import com.navya.service.Principal;
import com.navya.service.RateLimiter;
import com.navya.util.Json;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** All HTTP and WebSocket routes. Handlers read the request, call one service method and return JSON. Rules live in the services. */
public final class Api {
    private static final Logger log = LoggerFactory.getLogger(Api.class);

    // ---- request bodies ----
    record SignupBody(String email, String name, String city, String password, Boolean consent) {}
    record LoginBody(String email, String password) {}
    record StatusBody(String status) {}
    record MessageBody(String body) {}
    record ChatStartBody(String listingId) {}
    record ReportBody(String listingId, String reason) {}
    record BoostBody(String token, String key) {}

    /** Sent with every response. Static files get them through the file config because Javalin skips after-handlers for them. */
    private static final Map<String, String> SECURITY_HEADERS = Map.of(
            "X-Content-Type-Options", "nosniff",
            "Referrer-Policy", "no-referrer",
            "X-Frame-Options", "DENY",
            "Content-Security-Policy", "default-src 'self'; img-src 'self' blob: data:; style-src 'self'; script-src 'self'; connect-src 'self' ws: wss:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");

    private final AppContext c;

    private Api(AppContext c) { this.c = c; }

    public static Javalin create(AppContext ctx) {
        Api api = new Api(ctx);
        Javalin app = Javalin.create(cfg -> api.configure(cfg));
        api.filters(app);
        api.errors(app);
        api.routes(app);
        return app;
    }

    private void configure(JavalinConfig cfg) {
        cfg.showJavalinBanner = false;
        cfg.jsonMapper(new JavalinJackson(Json.mapper(), false));
        cfg.http.maxRequestSize = 700_000L; // a resized photo is under 600 KB
        // Bounded: 64 request threads and 500 waiting requests. Past that the server refuses new work instead of growing memory.
        cfg.jetty.threadPool = new QueuedThreadPool(64, 8, 60_000, new ArrayBlockingQueue<>(500));
        cfg.staticFiles.add(s -> {
            s.hostedPath = "/";
            s.directory = "/public";
            s.location = Location.CLASSPATH;
            Map<String, String> h = new java.util.HashMap<>(SECURITY_HEADERS);
            h.put("Cache-Control", "no-cache"); // revalidate on every load so a new release shows up at once
            s.headers = h;
        });
    }

    // ---- filters ----

    private void filters(Javalin app) {
        app.before(ctx -> {
            String rid = UUID.randomUUID().toString().substring(0, 8);
            MDC.put("requestId", rid);
            ctx.header("X-Request-Id", rid);
            ctx.attribute("t0", System.nanoTime());
            c.metrics.requests.incrementAndGet();
        });
        app.before("/api/*", ctx -> {
            String h = ctx.header("Authorization");
            if (h != null && h.startsWith("Bearer ")) ctx.attribute("principal", c.auth.verify(h.substring(7).trim()));
        });
        app.after(ctx -> {
            SECURITY_HEADERS.forEach(ctx::header);
            if ("https".equalsIgnoreCase(ctx.header("X-Forwarded-Proto"))) ctx.header("Strict-Transport-Security", "max-age=31536000");
            if (ctx.path().startsWith("/api/")) ctx.header("Cache-Control", "no-store");
            Long t0 = ctx.attribute("t0");
            long ms = t0 == null ? -1 : (System.nanoTime() - t0) / 1_000_000;
            Principal p = ctx.attribute("principal");
            log.info("request method={} path={} status={} ms={} user={}", ctx.method(), ctx.path(), ctx.status().getCode(), ms, p == null ? "-" : p.userId());
            MDC.clear();
        });
    }

    private void errors(Javalin app) {
        app.exception(NavyaException.class, (e, ctx) -> {
            if (e.status() >= 500) {
                c.metrics.serverErrors.incrementAndGet();
                log.error("Server error {}", e.code(), e);
            }
            ctx.status(e.status()).json(Map.of("code", e.code(), "message", e.getMessage()));
        });
        app.exception(HttpResponseException.class, (e, ctx) ->
                ctx.status(e.getStatus()).json(Map.of("code", "BAD_REQUEST", "message", e.getStatus() == 413 ? "Request is too large" : "The request could not be read")));
        app.exception(Exception.class, (e, ctx) -> {
            c.metrics.serverErrors.incrementAndGet();
            log.error("Unexpected error", e);
            ctx.status(500).json(Map.of("code", "INTERNAL", "message", "Something went wrong"));
        });
    }

    // ---- helpers ----

    private static Principal me(Context ctx) {
        Principal p = ctx.attribute("principal");
        if (p == null) throw new UnauthorizedException("Please log in");
        return p;
    }

    private static Principal maybe(Context ctx) { return ctx.attribute("principal"); }

    /** First address in X-Forwarded-For (set by CloudFront and the load balancer), else the socket address. */
    private static String ip(Context ctx) {
        String x = ctx.header("X-Forwarded-For");
        return x != null && !x.isBlank() ? x.split(",")[0].trim() : ctx.ip();
    }

    private static <T> T body(Context ctx, Class<T> type) {
        try {
            T b = ctx.bodyAsClass(type);
            if (b == null) throw new InvalidInputException("Request body is required");
            return b;
        } catch (NavyaException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidInputException("Request body is not valid JSON"); // Jackson parse and type errors both land here
        }
    }

    private static Long longParam(Context ctx, String name) {
        String v = ctx.queryParam(name);
        if (v == null || v.isBlank()) return null;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            throw new InvalidInputException(name + " must be a whole number");
        }
    }

    private static Map<String, Object> userView(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id);
        m.put("email", u.email);
        m.put("name", u.name);
        m.put("city", u.city);
        m.put("role", u.role());
        return m;
    }

    private static String imageUrl(String key) { return key == null ? null : "/img/" + key; }

    private Map<String, Object> listingView(Listing l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.id());
        m.put("sellerId", l.sellerId());
        m.put("title", l.title());
        m.put("price", l.pricePaise() / 100);
        m.put("category", l.category());
        m.put("condition", l.condition());
        m.put("size", l.size());
        m.put("city", l.city());
        m.put("description", l.description());
        m.put("images", l.images().stream().map(k -> Map.of("key", k, "url", imageUrl(k))).toList());
        m.put("status", l.status());
        m.put("boosted", l.boostedAt(c.clock.instant()));
        m.put("createdAt", l.createdAt());
        return m;
    }

    private Map<String, Object> pageView(ListingRepo.Page p) {
        return Map.of("items", p.items().stream().map(this::listingView).toList(), "total", p.total(), "pageSize", ListingService.PAGE_SIZE);
    }

    // ---- routes ----

    private void routes(Javalin app) {
        app.get("/health", ctx -> {
            boolean ok = c.healthy();
            ctx.status(ok ? 200 : 503).json(Map.of("status", ok ? "ok" : "down", "storage", c.config.storageType()));
        });

        // accounts
        app.post("/api/auth/signup", ctx -> {
            c.signupLimiter.check(ip(ctx));
            SignupBody b = body(ctx, SignupBody.class);
            if (!Boolean.TRUE.equals(b.consent())) throw new InvalidInputException("Please confirm you are 18 or older and accept the Terms of Use and Privacy Notice");
            var m = c.auth.signup(b.email(), b.name(), b.city(), b.password());
            c.auth.acceptTerms(m);
            c.metrics.signups.incrementAndGet();
            ctx.status(201).json(Map.of("token", c.auth.token(m), "user", userView(m)));
        });
        app.post("/api/auth/login", ctx -> {
            LoginBody b = body(ctx, LoginBody.class);
            c.loginLimiter.check(ip(ctx) + "|" + (b.email() == null ? "" : b.email().toLowerCase()));
            var r = c.auth.login(b.email(), b.password());
            ctx.json(Map.of("token", r.token(), "user", userView(r.user())));
        });
        app.get("/api/me", ctx -> ctx.json(userView(c.auth.get(me(ctx).userId()))));

        // listings
        app.get("/api/listings", ctx -> {
            var q = new ListingService.Query(ctx.queryParam("q"), ctx.queryParam("category"), ctx.queryParam("city"), longParam(ctx, "min"), longParam(ctx, "max"),
                    "1".equals(ctx.queryParam("favs")), ctx.queryParam("sort"), longParam(ctx, "page") == null ? 0 : (int) Math.min(10_000, longParam(ctx, "page")));
            ctx.json(pageView(c.listings.search(maybe(ctx), q)));
        });
        app.get("/api/listings/mine", ctx -> ctx.json(c.listings.mine(me(ctx)).stream().map(this::listingView).toList()));
        app.get("/api/listings/{id}", ctx -> {
            Listing l = c.listings.get(maybe(ctx), ctx.pathParam("id"));
            Map<String, Object> v = listingView(l);
            User s = c.auth.get(l.sellerId());
            v.put("seller", Map.of("id", s.id, "name", s.name, "city", s.city));
            ctx.json(v);
        });
        app.post("/api/listings", ctx -> {
            Principal p = me(ctx);
            c.writeLimiter.check("w|" + p.userId());
            ctx.status(201).json(listingView(c.listings.create(p, body(ctx, ListingService.Input.class))));
        });
        app.put("/api/listings/{id}", ctx -> {
            Principal p = me(ctx);
            c.writeLimiter.check("w|" + p.userId());
            ctx.json(listingView(c.listings.update(p, ctx.pathParam("id"), body(ctx, ListingService.Input.class))));
        });
        app.post("/api/listings/{id}/status", ctx -> ctx.json(listingView(c.listings.setStatus(me(ctx), ctx.pathParam("id"), body(ctx, StatusBody.class).status()))));
        app.delete("/api/listings/{id}", ctx -> {
            c.listings.delete(me(ctx), ctx.pathParam("id"));
            ctx.status(204);
        });
        app.get("/api/sellers/{id}", ctx -> {
            String id = ctx.pathParam("id");
            User s = com.navya.util.Ids.valid(id) ? c.auth.get(id) : null;
            if (s == null) throw new NotFoundException("Seller not found");
            ctx.json(Map.of("seller", Map.of("id", s.id, "name", s.name, "city", s.city), "listings", c.listings.bySeller(id).items().stream().map(this::listingView).toList()));
        });

        // photos: upload (raw JPEG body) and serve
        app.post("/api/images", ctx -> {
            Principal p = me(ctx);
            c.uploadLimiter.check("u|" + p.userId());
            String key = c.listings.saveImage(p, ctx.bodyAsBytes());
            ctx.status(201).json(Map.of("key", key, "url", imageUrl(key)));
        });
        app.get("/img/{owner}/{file}", ctx -> {
            byte[] data = c.listings.image(ctx.pathParam("owner") + "/" + ctx.pathParam("file")).orElseThrow(() -> new NotFoundException("Image not found"));
            ctx.header("Cache-Control", "public, max-age=31536000, immutable"); // names are random and never reused
            ctx.contentType("image/jpeg").result(data);
        });

        // saved items
        app.get("/api/favourites", ctx -> ctx.json(c.listings.favouriteIds(me(ctx))));
        app.post("/api/favourites/{id}", ctx -> ctx.json(Map.of("saved", c.listings.toggleFavourite(me(ctx), ctx.pathParam("id")))));

        // chat
        app.post("/api/conversations", ctx -> {
            Principal p = me(ctx);
            c.writeLimiter.check("w|" + p.userId());
            ctx.json(Map.of("id", c.chats.start(p, body(ctx, ChatStartBody.class).listingId()).id()));
        });
        app.get("/api/conversations", ctx -> ctx.json(c.chats.inbox(me(ctx)).stream().map(this::inboxView).toList()));
        app.get("/api/conversations/{id}", ctx -> {
            Principal p = me(ctx);
            var conv = c.chats.open(p, ctx.pathParam("id"));
            Listing l = c.listings.get(p, conv.listingId());
            User o = c.auth.get(conv.otherOf(p.userId()));
            ctx.json(Map.of("id", conv.id(), "listing", Map.of("id", l.id(), "title", l.title(), "price", l.pricePaise() / 100), "other", Map.of("id", o.id, "name", o.name)));
        });
        app.get("/api/conversations/{id}/messages", ctx -> {
            Long after = longParam(ctx, "after");
            ctx.json(c.chats.messages(me(ctx), ctx.pathParam("id"), after == null ? 0 : after).stream()
                    .map(m -> Map.of("id", m.id(), "senderId", m.senderId(), "body", m.body(), "createdAt", m.createdAt())).toList());
        });
        app.post("/api/conversations/{id}/messages", ctx -> {
            Principal p = me(ctx);
            c.messageLimiter.check("m|" + p.userId());
            var m = c.chats.send(p, ctx.pathParam("id"), body(ctx, MessageBody.class).body());
            ctx.status(201).json(Map.of("id", m.id(), "senderId", m.senderId(), "body", m.body(), "createdAt", m.createdAt()));
        });
        // The first message on the socket carries the token (kept out of the URL so it never reaches access logs).
        app.ws("/ws/chat/{id}", ws -> {
            ws.onMessage(ctx -> {
                String id = ctx.pathParam("id");
                if (Boolean.TRUE.equals(ctx.attribute("auth"))) return; // later messages are keep-alive pings
                try {
                    JsonNode n = Json.mapper().readTree(ctx.message());
                    Principal p = c.auth.verify(n.path("token").asText(""));
                    c.chats.open(p, id);
                    ctx.attribute("auth", true);
                    c.hub.join(id, ctx);
                    ctx.send("{\"type\":\"ready\"}");
                } catch (Exception e) {
                    ctx.closeSession(1008, "unauthorized");
                }
            });
            ws.onClose(ctx -> c.hub.leave(ctx.pathParam("id"), ctx));
        });

        // reports and boosts
        app.post("/api/reports", ctx -> {
            Principal p = me(ctx);
            c.writeLimiter.check("w|" + p.userId());
            ReportBody b = body(ctx, ReportBody.class);
            c.reports.report(p, b.listingId(), b.reason());
            ctx.status(201).json(Map.of("ok", true));
        });
        app.get("/api/boost/quote", ctx -> ctx.json(Map.of("price", c.boosts.quote() / 100.0, "days", c.config.boostDays())));
        app.post("/api/listings/{id}/boost", ctx -> {
            Principal p = me(ctx);
            c.payLimiter.check("p|" + p.userId());
            BoostBody b = body(ctx, BoostBody.class);
            BoostService.Result r = c.boosts.boost(p, ctx.pathParam("id"), b.token(), b.key());
            ctx.status(r.status().equals("paid") ? 200 : 202).json(Map.of("status", r.status(), "message", r.message(), "orderId", r.order().id(), "amount", r.order().amountPaise() / 100.0));
        });
        app.get("/api/orders", ctx -> ctx.json(c.boosts.history(me(ctx)).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.order().id());
            m.put("listingId", r.order().listingId());
            m.put("listingTitle", r.listingTitle());
            m.put("amount", r.order().amountPaise() / 100.0);
            m.put("status", r.order().status());
            m.put("createdAt", r.order().createdAt());
            return m;
        }).toList()));

        // moderation and operations (Navya team only)
        app.get("/api/admin/reports", ctx -> ctx.json(c.reports.open(me(ctx)).stream().map(r -> Map.of("id", r.report().id(), "listingId", r.report().listingId(),
                "listingTitle", r.listingTitle(), "listingStatus", r.listingStatus(), "reason", r.report().reason(), "createdAt", r.report().createdAt())).toList()));
        app.post("/api/admin/reports/{id}/{action}", ctx -> {
            c.reports.resolve(me(ctx), ctx.pathParam("id"), ctx.pathParam("action"));
            ctx.json(Map.of("ok", true));
        });
        app.get("/api/admin/audit", ctx -> {
            me(ctx).requireAdmin();
            ctx.json(c.audit.tail(100));
        });
        app.get("/api/admin/stats", ctx -> {
            me(ctx).requireAdmin();
            Map<String, Object> m = new LinkedHashMap<>(c.metrics.snapshot());
            m.put("chatSessions", c.hub.sessionCount());
            m.put("noticesDropped", c.notifier.dropped());
            ctx.json(m);
        });
        app.get("/api/admin/export.csv", ctx -> {
            me(ctx).requireAdmin();
            ctx.contentType("text/csv").header("Content-Disposition", "attachment; filename=\"navya-listings.csv\"").result(c.exports.csv());
        });
        app.post("/api/admin/export", ctx -> {
            Principal p = me(ctx);
            p.requireAdmin();
            String key = c.exports.exportToStorage();
            c.audit.record(p.userId(), "export", key);
            ctx.json(Map.of("key", key));
        });
    }

    private Map<String, Object> inboxView(ChatRepo.InboxRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.conversation().id());
        m.put("listingId", r.conversation().listingId());
        m.put("listingTitle", r.listingTitle());
        m.put("listingImage", imageUrl(r.listingImage()));
        m.put("otherName", r.otherName());
        m.put("lastBody", r.lastBody());
        m.put("lastAt", r.lastAt());
        return m;
    }
}
