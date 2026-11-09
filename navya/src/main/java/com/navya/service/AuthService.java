package com.navya.service;

import java.time.Instant;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.navya.domain.Admin;
import com.navya.domain.Member;
import com.navya.domain.Role;
import com.navya.domain.User;
import com.navya.exception.NotFoundException;
import com.navya.exception.UnauthorizedException;
import com.navya.repo.UserRepo;
import com.navya.util.Ids;
import com.navya.util.Validate;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import org.mindrot.jbcrypt.BCrypt;

/** Accounts, passwords and signed tokens. */
public final class AuthService {
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);
    /** Compared against when the email is unknown, so unknown and wrong-password take similar time. */
    private static final String DUMMY_HASH = BCrypt.hashpw("not-a-real-password", BCrypt.gensalt(10));

    public record LoginResult(String token, User user) {}

    private final UserRepo users;
    private final Clock clock;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public AuthService(UserRepo users, String jwtSecret, Clock clock) {
        this.users = users;
        this.clock = clock;
        this.algorithm = Algorithm.HMAC256(jwtSecret);
        // the verifier reads the same clock as the rest of the app, so expiry can be tested without waiting
        this.verifier = ((JWTVerifier.BaseVerification) JWT.require(algorithm).withIssuer("navya")).build(clock);
    }

    public Member signup(String email, String name, String city, String password) {
        Member m = new Member();
        fill(m, email, name, city, password);
        users.insert(m);
        return m;
    }

    /** Used at start-up to create the admin account from the environment. Does nothing if the email exists. */
    public Admin ensureAdmin(String email, String password) {
        var existing = users.byEmail(email);
        if (existing.isPresent()) return existing.get() instanceof Admin a ? a : null;
        Admin a = new Admin();
        fill(a, email, "Navya team", "", password);
        users.insert(a);
        return a;
    }

    private void fill(User u, String email, String name, String city, String password) {
        u.id = Ids.newId();
        u.email = Validate.email(email);
        u.name = Validate.text(name, "Name", 1, 60);
        u.city = Validate.optionalText(city, "City", 60);
        u.passwordHash = BCrypt.hashpw(Validate.password(password), BCrypt.gensalt(10));
        u.createdAt = clock.instant();
    }

    /** Version of the Terms of Use and Privacy Notice a new member accepts. Change it when those documents change. */
    public static final String TERMS_VERSION = "2026-11-25";

    /** Records that the member confirmed they are 18 or older and accepted the Terms and Privacy Notice. */
    public void acceptTerms(User u) {
        Instant now = clock.instant();
        users.recordConsent(u.id, TERMS_VERSION, now);
        u.consentedAt = now;
        u.termsVersion = TERMS_VERSION;
    }

    public LoginResult login(String email, String password) {
        String e = email == null ? "" : email.trim().toLowerCase();
        User u = users.byEmail(e).orElse(null);
        // one check either way, one message either way: the response never says which part was wrong
        boolean ok = BCrypt.checkpw(password == null ? "" : password, u == null ? DUMMY_HASH : u.passwordHash);
        if (u == null || !ok) throw new UnauthorizedException("Email or password is wrong");
        return new LoginResult(token(u), u);
    }

    public String token(User u) {
        return JWT.create().withIssuer("navya").withSubject(u.id).withClaim("role", u.role().name())
                .withIssuedAt(Date.from(clock.instant())).withExpiresAt(Date.from(clock.instant().plus(TOKEN_LIFETIME))).sign(algorithm);
    }

    /** Checks the signature and expiry. Throws UnauthorizedException for anything else. */
    public Principal verify(String token) {
        try {
            DecodedJWT j = verifier.verify(token);
            return new Principal(j.getSubject(), Role.valueOf(j.getClaim("role").asString()));
        } catch (JWTVerificationException | IllegalArgumentException | NullPointerException e) {
            throw new UnauthorizedException("Please log in again");
        }
    }

    public User get(String id) { return users.byId(id).orElseThrow(() -> new NotFoundException("User not found")); }

    public java.util.Map<String, User> getAll(java.util.List<String> ids) {
        java.util.Map<String, User> m = new java.util.HashMap<>();
        for (User u : users.byIds(ids)) m.put(u.id, u);
        return m;
    }
}
