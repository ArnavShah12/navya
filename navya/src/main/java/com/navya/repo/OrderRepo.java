package com.navya.repo;

import com.navya.domain.Order;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class OrderRepo {
    public record Row(Order order, String listingTitle) {}

    private final Db db;
    private final ListingRepo listings;

    public OrderRepo(Db db, ListingRepo listings) {
        this.db = db;
        this.listings = listings;
    }

    private static Order map(ResultSet rs) throws SQLException {
        return new Order(rs.getString("id"), rs.getString("user_id"), rs.getString("listing_id"), rs.getLong("amount_paise"), rs.getString("status"),
                rs.getString("idempotency_key"), rs.getTimestamp("created_at").toInstant());
    }

    public Optional<Order> byKey(String key) { return db.one("SELECT * FROM orders WHERE idempotency_key = ?", OrderRepo::map, key); }

    public Optional<Order> byId(String id) { return db.one("SELECT * FROM orders WHERE id = ?", OrderRepo::map, id); }

    /** False when an order with this idempotency key already exists. */
    public boolean insertPending(Order o) {
        return db.update("INSERT INTO orders (id, user_id, listing_id, amount_paise, idempotency_key, created_at) VALUES (?,?,?,?,?,?) ON CONFLICT (idempotency_key) DO NOTHING",
                o.id(), o.userId(), o.listingId(), o.amountPaise(), o.idempotencyKey(), o.createdAt()) > 0; // the app's clock, not the database's, so every time rule uses one clock
    }

    /** Moves pending to paid and extends the boost in one transaction. Only the first caller wins, so a boost is never applied twice. */
    public boolean markPaid(Order o, Instant now, int days) {
        return db.tx(c -> {
            int n = Db.update(c, "UPDATE orders SET status = 'paid' WHERE id = ? AND status = 'pending'", o.id());
            if (n == 0) return false;
            if (o.listingId() != null) listings.boostUntil(c, o.listingId(), now, days);
            return true;
        });
    }

    public boolean markFailed(String id) { return db.update("UPDATE orders SET status = 'failed' WHERE id = ? AND status = 'pending'", id) > 0; }

    public List<Order> pendingBefore(Instant cutoff) { return db.list("SELECT * FROM orders WHERE status = 'pending' AND created_at < ?", OrderRepo::map, cutoff); }

    public List<Row> byUser(String userId) {
        return db.list("SELECT o.*, l.title AS ltitle FROM orders o LEFT JOIN listings l ON l.id = o.listing_id WHERE o.user_id = ? ORDER BY o.created_at DESC",
                rs -> new Row(map(rs), rs.getString("ltitle")), userId);
    }
}
