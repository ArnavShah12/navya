package com.navya.repo;

import com.navya.domain.Listing;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ListingRepo {
    private static final String SELECT = "SELECT l.*, ARRAY(SELECT i.image_key FROM listing_images i WHERE i.listing_id = l.id ORDER BY i.position) AS images FROM listings l ";

    /** What the home page asks for. Null or blank fields are ignored. */
    public record Filter(List<String> words, String category, String city, Long minPaise, Long maxPaise, String favouritesOf, String sellerId,
                         boolean includeHidden, String sort, int limit, int offset) {}

    public record Page(List<Listing> items, long total) {}

    private final Db db;

    public ListingRepo(Db db) { this.db = db; }

    private static Listing map(ResultSet rs) throws SQLException {
        java.sql.Timestamp b = rs.getTimestamp("boosted_until");
        return new Listing(rs.getString("id"), rs.getString("seller_id"), rs.getString("title"), rs.getLong("price_paise"),
                rs.getString("category"), rs.getString("condition"), rs.getString("size"), rs.getString("city"),
                rs.getString("description"), Arrays.asList((String[]) rs.getArray("images").getArray()), rs.getString("status"),
                b == null ? null : b.toInstant(), rs.getTimestamp("created_at").toInstant());
    }

    public void insert(Listing l) {
        db.tx(c -> {
            Db.update(c, "INSERT INTO listings (id, seller_id, title, price_paise, category, condition, size, city, description, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    l.id(), l.sellerId(), l.title(), l.pricePaise(), l.category(), l.condition(), l.size(), l.city(), l.description(), l.createdAt(), l.createdAt());
            images(c, l.id(), l.images());
            return null;
        });
    }

    public boolean update(Listing l) {
        return db.tx(c -> {
            int n = Db.update(c, "UPDATE listings SET title=?, price_paise=?, category=?, condition=?, size=?, city=?, description=?, updated_at=now() WHERE id=?",
                    l.title(), l.pricePaise(), l.category(), l.condition(), l.size(), l.city(), l.description(), l.id());
            if (n == 0) return false;
            Db.update(c, "DELETE FROM listing_images WHERE listing_id = ?", l.id());
            images(c, l.id(), l.images());
            return true;
        });
    }

    private static void images(Connection c, String listingId, List<String> keys) throws SQLException {
        for (int i = 0; i < keys.size(); i++) Db.update(c, "INSERT INTO listing_images (listing_id, position, image_key) VALUES (?,?,?)", listingId, i, keys.get(i));
    }

    public Optional<Listing> get(String id) { return db.one(SELECT + "WHERE l.id = ?", ListingRepo::map, id); }

    public boolean setStatus(String id, String status) { return db.update("UPDATE listings SET status = ?, updated_at = now() WHERE id = ?", status, id) > 0; }

    public boolean delete(String id) { return db.update("DELETE FROM listings WHERE id = ?", id) > 0; }

    /** Marks listings older than the cutoff as expired. Safe to run on every instance at once. Returns how many changed. */
    public int expireOlderThan(Instant cutoff) {
        return db.update("UPDATE listings SET status = 'expired', updated_at = now() WHERE status = 'available' AND created_at < ?", cutoff);
    }

    public Page search(Filter f, Instant now) {
        List<Object> args = new ArrayList<>();
        StringBuilder w = new StringBuilder(f.includeHidden() ? " WHERE true" : " WHERE l.status IN ('available','sold')");
        if (f.sellerId() != null) { w.append(" AND l.seller_id = ?"); args.add(f.sellerId()); }
        for (String word : f.words()) {
            w.append(" AND (l.title || ' ' || l.category || ' ' || l.description || ' ' || l.condition || ' ' || l.size || ' ' || l.city) ILIKE ? ESCAPE '\\'");
            args.add("%" + like(word) + "%");
        }
        if (f.category() != null && !f.category().isBlank()) { w.append(" AND l.category = ?"); args.add(f.category()); }
        if (f.city() != null && !f.city().isBlank()) { w.append(" AND l.city ILIKE ? ESCAPE '\\'"); args.add("%" + like(f.city().trim()) + "%"); }
        if (f.minPaise() != null) { w.append(" AND l.price_paise >= ?"); args.add(f.minPaise()); }
        if (f.maxPaise() != null) { w.append(" AND l.price_paise <= ?"); args.add(f.maxPaise()); }
        if (f.favouritesOf() != null) { w.append(" AND l.id IN (SELECT listing_id FROM favourites WHERE user_id = ?)"); args.add(f.favouritesOf()); }

        long total = db.one("SELECT count(*) AS n FROM listings l" + w, rs -> rs.getLong("n"), args.toArray()).orElse(0L);

        String order = switch (f.sort() == null ? "" : f.sort()) {
            case "low" -> " ORDER BY l.price_paise ASC, l.created_at DESC";
            case "high" -> " ORDER BY l.price_paise DESC, l.created_at DESC";
            // paid boosts first, then newest
            default -> " ORDER BY (l.boosted_until IS NOT NULL AND l.boosted_until > ?) DESC, l.created_at DESC";
        };
        List<Object> a2 = new ArrayList<>(args);
        if (order.contains("?")) a2.add(now);
        a2.add(f.limit());
        a2.add(f.offset());
        List<Listing> items = db.list(SELECT + w + order + " LIMIT ? OFFSET ?", ListingRepo::map, a2.toArray());
        return new Page(items, total);
    }

    /** Escapes LIKE wildcards so a search for "100%" matches the text, not everything. */
    static String like(String s) { return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }

    public void boostUntil(Connection c, String id, Instant now, int days) throws SQLException {
        Db.update(c, "UPDATE listings SET boosted_until = greatest(coalesce(boosted_until, ?), ?) + (? * interval '1 day') WHERE id = ?", now, now, days, id);
    }

    // ---- saved items ----

    /** Adds the listing to the user's saved items, or removes it if already saved. Returns true when it is now saved. */
    public boolean toggleFavourite(String userId, String listingId) {
        int removed = db.update("DELETE FROM favourites WHERE user_id = ? AND listing_id = ?", userId, listingId);
        if (removed > 0) return false;
        db.update("INSERT INTO favourites (user_id, listing_id) VALUES (?,?) ON CONFLICT DO NOTHING", userId, listingId);
        return true;
    }

    public Set<String> favouriteIds(String userId) { return Set.copyOf(db.list("SELECT listing_id FROM favourites WHERE user_id = ?", rs -> rs.getString(1), userId)); }

    public List<String> allKeysOf(String listingId) { return db.list("SELECT image_key FROM listing_images WHERE listing_id = ? ORDER BY position", rs -> rs.getString(1), listingId); }
}
