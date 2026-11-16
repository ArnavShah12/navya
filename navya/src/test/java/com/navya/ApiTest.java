package com.navya;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.navya.util.Json;
import com.navya.web.Api;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** End-to-end over real HTTP: the same calls the browser makes. */
class ApiTest {
    MutableClock clock;
    AppContext ctx;
    Javalin app;
    String base;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach void setUp() {
        clock = TestSupport.clock();
        ctx = TestSupport.context(clock, Duration.ofSeconds(5), 300);
        ctx.startBackground();
        app = Api.create(ctx).start(0);
        base = "http://localhost:" + app.port();
    }

    @AfterEach void tearDown() {
        app.stop();
        ctx.close();
    }

    record Res(int status, JsonNode json, String text, HttpResponse<String> raw) {}

    Res call(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (body != null) b.header("Content-Type", "application/json");
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : Json.mapper().writeValueAsString(body)));
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode j = null;
        try { j = Json.mapper().readTree(r.body()); } catch (Exception e) { /* not JSON */ }
        return new Res(r.statusCode(), j, r.body(), r);
    }

    String signup(String name) throws Exception {
        Res r = call("POST", "/api/auth/signup", null, java.util.Map.of("email", name.toLowerCase() + "@example.com", "name", name, "city", "Pune", "password", "password123", "consent", true));
        assertThat(r.status()).isEqualTo(201);
        return r.json().get("token").asText();
    }

    String post(String token, String title, int price) throws Exception {
        Res r = call("POST", "/api/listings", token, java.util.Map.of("title", title, "price", price, "category", "Clothes", "condition", "Good", "size", "M", "city", "Pune", "description", "desc", "images", java.util.List.of()));
        assertThat(r.status()).isEqualTo(201);
        return r.json().get("id").asText();
    }

    @Test
    @DisplayName("TC-54 full flow: sign up, post, search, save, chat, report, boost, order history")
    void mainFlow() throws Exception {
        String anna = signup("Anna"), ben = signup("Ben");
        String id = post(anna, "Denim jacket", 900);

        assertThat(call("GET", "/api/listings?q=denim", null, null).json.get("total").asInt()).isEqualTo(1);
        assertThat(call("GET", "/api/listings/" + id, null, null).json.get("seller").get("name").asText()).isEqualTo("Anna");

        assertThat(call("POST", "/api/favourites/" + id, ben, null).json.get("saved").asBoolean()).isTrue();
        assertThat(call("GET", "/api/favourites", ben, null).json.get(0).asText()).isEqualTo(id);

        String conv = call("POST", "/api/conversations", ben, java.util.Map.of("listingId", id)).json.get("id").asText();
        assertThat(call("POST", "/api/conversations/" + conv + "/messages", ben, java.util.Map.of("body", "Still there?")).status()).isEqualTo(201);
        assertThat(call("GET", "/api/conversations", anna, null).json.get(0).get("lastBody").asText()).isEqualTo("Still there?");
        assertThat(call("GET", "/api/conversations/" + conv + "/messages", anna, null).json).hasSize(1);

        assertThat(call("POST", "/api/reports", ben, java.util.Map.of("listingId", id, "reason", "test")).status()).isEqualTo(201);

        Res boost = call("POST", "/api/listings/" + id + "/boost", anna, java.util.Map.of("token", "tok_success", "key", "api-1"));
        assertThat(boost.status()).isEqualTo(200);
        assertThat(boost.json.get("status").asText()).isEqualTo("paid");
        assertThat(call("GET", "/api/listings/" + id, null, null).json.get("boosted").asBoolean()).isTrue();
        assertThat(call("GET", "/api/orders", anna, null).json.get(0).get("status").asText()).isEqualTo("paid");
    }

    @Test
    @DisplayName("TC-55 payment outcomes over HTTP: decline is 402, slow bank is 202 then settles")
    void paymentStatuses() throws Exception {
        String anna = signup("Anna");
        String id = post(anna, "Jacket", 100);
        assertThat(call("POST", "/api/listings/" + id + "/boost", anna, java.util.Map.of("token", "tok_declined", "key", "d1")).status()).isEqualTo(402);
        Res slow = call("POST", "/api/listings/" + id + "/boost", anna, java.util.Map.of("token", "tok_timeout", "key", "s1"));
        assertThat(slow.status()).isEqualTo(202);
        assertThat(slow.json.get("status").asText()).isEqualTo("pending");
        clock.advance(Duration.ofSeconds(6));
        assertThat(ctx.boosts.reconcile()).isEqualTo(1);
        assertThat(call("GET", "/api/orders", anna, null).json.get(0).get("status").asText()).isEqualTo("paid");
    }

    @Test
    @DisplayName("TC-56 requests without a token, or with a bad one, are 401")
    void auth() throws Exception {
        assertThat(call("GET", "/api/me", null, null).status()).isEqualTo(401);
        assertThat(call("GET", "/api/listings/mine", null, null).status()).isEqualTo(401);
        assertThat(call("GET", "/api/me", "not.a.token", null).status()).isEqualTo(401);
        assertThat(call("POST", "/api/listings", null, "{}").status()).isEqualTo(401);
        // an unsigned token with "alg":"none" is refused too
        String none = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes()) + "."
                + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"iss\":\"navya\",\"sub\":\"x\",\"role\":\"ADMIN\"}".getBytes()) + ".";
        assertThat(call("GET", "/api/admin/stats", none, null).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("TC-57 a member cannot edit, delete, sell-mark or boost someone else's listing")
    void ownership() throws Exception {
        String anna = signup("Anna"), ben = signup("Ben");
        String id = post(anna, "Jacket", 100);
        var body = java.util.Map.of("title", "x", "price", 1, "category", "Clothes", "condition", "Good", "city", "Pune", "description", "d");
        assertThat(call("PUT", "/api/listings/" + id, ben, body).status()).isEqualTo(403);
        assertThat(call("DELETE", "/api/listings/" + id, ben, null).status()).isEqualTo(403);
        assertThat(call("POST", "/api/listings/" + id + "/status", ben, java.util.Map.of("status", "sold")).status()).isEqualTo(403);
        assertThat(call("POST", "/api/listings/" + id + "/boost", ben, java.util.Map.of("token", "tok_success", "key", "k")).status()).isEqualTo(403);
        assertThat(call("GET", "/api/listings/" + id, null, null).json.get("title").asText()).isEqualTo("Jacket");
    }

    @Test
    @DisplayName("TC-58 admin pages are for admins only")
    void admin() throws Exception {
        String ben = signup("Ben");
        for (String p : new String[] {"/api/admin/reports", "/api/admin/stats", "/api/admin/audit", "/api/admin/export.csv"})
            assertThat(call("GET", p, ben, null).status()).as(p).isEqualTo(403);
        assertThat(call("POST", "/api/admin/export", ben, null).status()).isEqualTo(403);
        ctx.auth.ensureAdmin("admin@example.com", "password123");
        String admin = call("POST", "/api/auth/login", null, java.util.Map.of("email", "admin@example.com", "password", "password123")).json.get("token").asText();
        assertThat(call("GET", "/api/admin/stats", admin, null).status()).isEqualTo(200);
        assertThat(call("GET", "/api/admin/export.csv", admin, null).raw.headers().firstValue("Content-Type").orElse("")).startsWith("text/csv");
    }

    @Test
    @DisplayName("TC-59 a stranger gets 404 for someone else's chat (no way to probe which ids exist)")
    void idor() throws Exception {
        String anna = signup("Anna"), ben = signup("Ben"), cara = signup("Cara");
        String id = post(anna, "Jacket", 100);
        String conv = call("POST", "/api/conversations", ben, java.util.Map.of("listingId", id)).json.get("id").asText();
        assertThat(call("GET", "/api/conversations/" + conv, cara, null).status()).isEqualTo(404);
        assertThat(call("GET", "/api/conversations/" + conv + "/messages", cara, null).status()).isEqualTo(404);
        assertThat(call("POST", "/api/conversations/" + conv + "/messages", cara, java.util.Map.of("body", "hi")).status()).isEqualTo(404);
        assertThat(call("GET", "/api/conversations/" + com.navya.util.Ids.newId(), cara, null).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("TC-60 hostile input: SQL text, script tags and bad JSON never cause a 500")
    void hostileInput() throws Exception {
        String anna = signup("Anna");
        assertThat(call("GET", "/api/listings?q=%27%3B%20DROP%20TABLE%20users%3B%20--", null, null).status()).isEqualTo(200);
        assertThat(call("GET", "/api/listings?min=abc", null, null).status()).isEqualTo(400);
        assertThat(call("GET", "/api/listings/1%20OR%201%3D1", null, null).status()).isEqualTo(404);
        assertThat(call("POST", "/api/listings", anna, "{not json").status()).isEqualTo(400);
        assertThat(call("POST", "/api/listings", anna, "[]").status()).isIn(400);
        assertThat(call("POST", "/api/auth/login", null, "").status()).isEqualTo(400);
        String xss = post(anna, "<script>alert(1)</script>", 10);
        Res r = call("GET", "/api/listings/" + xss, null, null);
        assertThat(r.json.get("title").asText()).isEqualTo("<script>alert(1)</script>"); // stored as text, the page escapes it
        assertThat(r.raw.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        assertThat(ctx.metrics.serverErrors.get()).isZero();
    }

    @Test
    @DisplayName("TC-61 security headers are on every response")
    void headers() throws Exception {
        var h = call("GET", "/", null, null).raw.headers();
        assertThat(h.firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(h.firstValue("X-Frame-Options")).contains("DENY");
        assertThat(h.firstValue("Content-Security-Policy").orElse("")).contains("script-src 'self'").doesNotContain("unsafe-inline");
        assertThat(call("GET", "/api/listings", null, null).raw.headers().firstValue("Cache-Control")).contains("no-store");
    }

    @Test
    @DisplayName("TC-62 login is rate limited after 5 tries")
    void loginLimit() throws Exception {
        signup("Anna");
        int last = 0;
        for (int i = 0; i < 8; i++) last = call("POST", "/api/auth/login", null, java.util.Map.of("email", "anna@example.com", "password", "wrong-wrong")).status();
        assertThat(last).isEqualTo(429);
    }

    @Test
    @DisplayName("TC-63 photo upload over HTTP: JPEG stored and served, text file refused, no login refused")
    void upload() throws Exception {
        String anna = signup("Anna");
        HttpRequest up = HttpRequest.newBuilder(URI.create(base + "/api/images")).header("Authorization", "Bearer " + anna).header("Content-Type", "image/jpeg")
                .POST(HttpRequest.BodyPublishers.ofByteArray(TestSupport.jpeg())).build();
        HttpResponse<String> r = http.send(up, HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(201);
        String url = Json.mapper().readTree(r.body()).get("url").asText();
        HttpResponse<byte[]> img = http.send(HttpRequest.newBuilder(URI.create(base + url)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(img.statusCode()).isEqualTo(200);
        assertThat(img.headers().firstValue("Content-Type")).contains("image/jpeg");
        assertThat(img.headers().firstValue("Cache-Control").orElse("")).contains("immutable");
        assertThat(img.body()).isEqualTo(TestSupport.jpeg());
        assertThat(http.send(HttpRequest.newBuilder(URI.create(base + "/img/x/..%2F..%2Fetc%2Fpasswd")).build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isIn(400, 404);
        HttpRequest bad = HttpRequest.newBuilder(URI.create(base + "/api/images")).header("Authorization", "Bearer " + anna).POST(HttpRequest.BodyPublishers.ofString("hello")).build();
        assertThat(http.send(bad, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);
        HttpRequest anon = HttpRequest.newBuilder(URI.create(base + "/api/images")).POST(HttpRequest.BodyPublishers.ofByteArray(TestSupport.jpeg())).build();
        assertThat(http.send(anon, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        HttpRequest huge = HttpRequest.newBuilder(URI.create(base + "/api/images")).header("Authorization", "Bearer " + anna).POST(HttpRequest.BodyPublishers.ofByteArray(new byte[800_000])).build();
        assertThat(http.send(huge, HttpResponse.BodyHandlers.ofString()).statusCode()).isIn(400, 413);
    }

    @Test
    @DisplayName("TC-64 health answers 200 and says ok while the database is up")
    void health() throws Exception {
        Res r = call("GET", "/health", null, null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json.get("status").asText()).isEqualTo("ok");
    }

    @Test
    @DisplayName("TC-69 seller profile, quote, me, audit, export and stats answer correctly; bad ids are 404")
    void smallRoutes() throws Exception {
        String anna = signup("Anna");
        String id = post(anna, "Jacket", 100);
        String annaId = call("GET", "/api/me", anna, null).json.get("id").asText();
        Res seller = call("GET", "/api/sellers/" + annaId, null, null);
        assertThat(seller.json.get("seller").get("name").asText()).isEqualTo("Anna");
        assertThat(seller.json.get("listings")).hasSize(1);
        assertThat(seller.json.get("seller").has("email")).isFalse(); // the profile never shows the email
        assertThat(call("GET", "/api/sellers/not-an-id", null, null).status()).isEqualTo(404);
        assertThat(call("GET", "/api/sellers/" + com.navya.util.Ids.newId(), null, null).status()).isEqualTo(404);
        assertThat(call("GET", "/api/boost/quote", null, null).json.get("days").asInt()).isEqualTo(7);
        assertThat(call("GET", "/api/listings/mine", anna, null).json).hasSize(1);
        assertThat(call("POST", "/api/listings/" + id + "/status", anna, java.util.Map.of("status", "sold")).json.get("status").asText()).isEqualTo("sold");

        ctx.auth.ensureAdmin("admin@example.com", "password123");
        String admin = call("POST", "/api/auth/login", null, java.util.Map.of("email", "admin@example.com", "password", "password123")).json.get("token").asText();
        Res export = call("POST", "/api/admin/export", admin, null);
        assertThat(export.status()).isEqualTo(200);
        assertThat(ctx.storage.exists(export.json.get("key").asText())).isTrue();
        assertThat(call("GET", "/api/admin/audit", admin, null).json.toString()).contains("export");
        assertThat(call("GET", "/api/admin/stats", admin, null).json.get("signups").asInt()).isEqualTo(1);
        assertThat(call("DELETE", "/api/listings/" + id, admin, null).status()).isEqualTo(204); // an admin can remove a listing
    }

    // ---- live chat ----

    private static final class Listener implements WebSocket.Listener {
        final LinkedBlockingQueue<String> got = new LinkedBlockingQueue<>();
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            got.add(data.toString());
            ws.request(1);
            return null;
        }
        @Override public void onOpen(WebSocket ws) { ws.request(1); }
    }

    @Test
    @DisplayName("TC-65 live chat: a message sent on instance A pings a browser connected to instance B")
    void liveChatAcrossInstances() throws Exception {
        AppContext ctxB = TestSupport.secondInstance(ctx, clock);
        ctxB.startBackground();
        Javalin appB = Api.create(ctxB).start(0);
        try {
            String anna = signup("Anna"), ben = signup("Ben");
            String id = post(anna, "Jacket", 100);
            String conv = call("POST", "/api/conversations", ben, java.util.Map.of("listingId", id)).json.get("id").asText();

            // Anna's browser is connected to instance B
            Listener l = new Listener();
            WebSocket ws = http.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:" + appB.port() + "/ws/chat/" + conv), l).get(5, TimeUnit.SECONDS);
            ws.sendText("{\"token\":\"" + anna + "\"}", true);
            assertThat(l.got.poll(5, TimeUnit.SECONDS)).contains("ready");
            Thread.sleep(500); // let the LISTEN connection settle

            // Ben posts through instance A
            assertThat(call("POST", "/api/conversations/" + conv + "/messages", ben, java.util.Map.of("body", "hello")).status()).isEqualTo(201);
            assertThat(l.got.poll(5, TimeUnit.SECONDS)).contains("new");
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        } finally {
            appB.stop();
            ctxB.close();
        }
    }

    @Test
    @DisplayName("TC-66 live chat refuses a socket with a bad token or for someone else's chat")
    void liveChatAuth() throws Exception {
        String anna = signup("Anna"), ben = signup("Ben"), cara = signup("Cara");
        String id = post(anna, "Jacket", 100);
        String conv = call("POST", "/api/conversations", ben, java.util.Map.of("listingId", id)).json.get("id").asText();
        for (String tok : new String[] {"garbage", cara}) {
            Listener l = new Listener();
            WebSocket ws = http.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:" + app.port() + "/ws/chat/" + conv), l).get(5, TimeUnit.SECONDS);
            ws.sendText("{\"token\":\"" + tok + "\"}", true);
            assertThat(l.got.poll(2, TimeUnit.SECONDS)).isNull(); // never gets "ready"
            Thread.sleep(300);
            assertThat(ws.isInputClosed() || ws.isOutputClosed()).isTrue();
        }
        assertThat(ctx.hub.sessionCount()).isZero();
    }

    @Test
    @DisplayName("TC-70 sign-up is refused unless the member confirms 18+ and accepts the terms")
    void signupNeedsConsent() throws Exception {
        for (Object consent : new Object[] {null, false, "yes"}) {
            var body = new java.util.HashMap<String, Object>(java.util.Map.of("email", "nocon@example.com", "name", "No", "city", "Pune", "password", "password123"));
            if (consent != null) body.put("consent", consent);
            Res r = call("POST", "/api/auth/signup", null, body);
            assertThat(r.status()).isEqualTo(400);
        }
        // nothing was created, so the same email can still sign up with consent
        Res ok = call("POST", "/api/auth/signup", null, java.util.Map.of("email", "nocon@example.com", "name", "No", "city", "Pune", "password", "password123", "consent", true));
        assertThat(ok.status()).isEqualTo(201);
    }

    @Test
    @DisplayName("TC-71 the consent time and terms version are stored on the account")
    void consentIsRecorded() throws Exception {
        signup("Anna");
        var u = ctx.auth.login("anna@example.com", "password123").user();
        assertThat(u.consentedAt).isEqualTo(clock.instant());
        assertThat(u.termsVersion).isEqualTo(com.navya.service.AuthService.TERMS_VERSION);
    }

    @Test
    @DisplayName("TC-72 terms, privacy and grievance pages are public, name the officer, and the home page links to them")
    void legalPagesAreServed() throws Exception {
        for (String page : new String[] {"/terms.html", "/privacy.html", "/grievance.html"}) {
            Res r = call("GET", page, null, null);
            assertThat(r.status()).as(page).isEqualTo(200);
            assertThat(r.text()).contains("Navya").contains("Version " + com.navya.service.AuthService.TERMS_VERSION);
        }
        assertThat(call("GET", "/grievance.html", null, null).text()).contains("Arnav Shah").contains("mailto:");
        String home = call("GET", "/", null, null).text();
        assertThat(home).contains("/terms.html").contains("/privacy.html").contains("/grievance.html");
    }
}
