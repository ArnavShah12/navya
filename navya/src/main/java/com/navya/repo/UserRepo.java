package com.navya.repo;

import com.navya.domain.Admin;
import com.navya.domain.Member;
import com.navya.domain.Role;
import com.navya.domain.User;
import com.navya.exception.DuplicateEmailException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class UserRepo {
    private final Db db;

    public UserRepo(Db db) { this.db = db; }

    private static User map(ResultSet rs) throws SQLException {
        User u = Role.valueOf(rs.getString("role")) == Role.ADMIN ? new Admin() : new Member();
        u.id = rs.getString("id");
        u.email = rs.getString("email");
        u.name = rs.getString("name");
        u.city = rs.getString("city");
        u.passwordHash = rs.getString("password_hash");
        u.createdAt = rs.getTimestamp("created_at").toInstant();
        var consented = rs.getTimestamp("consented_at");
        u.consentedAt = consented == null ? null : consented.toInstant();
        u.termsVersion = rs.getString("terms_version");
        return u;
    }

    public void insert(User u) {
        try {
            db.update("INSERT INTO users (id, email, name, city, password_hash, role) VALUES (?,?,?,?,?,?)",
                    u.id, u.email, u.name, u.city, u.passwordHash, u.role().name());
        } catch (RuntimeException e) {
            if (Db.isDuplicate(e)) throw new DuplicateEmailException("An account with this email already exists");
            throw e;
        }
    }

    public void recordConsent(String id, String version, java.time.Instant at) {
        db.update("UPDATE users SET consented_at = ?, terms_version = ? WHERE id = ?", java.sql.Timestamp.from(at), version, id);
    }

    public Optional<User> byId(String id) { return db.one("SELECT * FROM users WHERE id = ?", UserRepo::map, id); }

    public Optional<User> byEmail(String email) { return db.one("SELECT * FROM users WHERE lower(email) = lower(?)", UserRepo::map, email); }

    public List<User> byIds(List<String> ids) {
        if (ids.isEmpty()) return List.of();
        return db.list("SELECT * FROM users WHERE id = ANY (?::text[])", UserRepo::map, ids.toArray(new String[0]));
    }
}
