package com.navya.repo;

import com.navya.domain.Conversation;
import com.navya.domain.Message;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class ChatRepo {
    /** The channel every app instance LISTENs on. The payload is the conversation id. */
    public static final String CHANNEL = "navya_msg";

    /** One line of the inbox. */
    public record InboxRow(Conversation conversation, String listingTitle, String listingImage, String otherName, String lastBody, Instant lastAt) {}

    private final Db db;

    public ChatRepo(Db db) { this.db = db; }

    private static Conversation conv(ResultSet rs) throws SQLException {
        return new Conversation(rs.getString("id"), rs.getString("listing_id"), rs.getString("buyer_id"), rs.getString("seller_id"), rs.getTimestamp("created_at").toInstant());
    }

    private static Message msg(ResultSet rs) throws SQLException {
        return new Message(rs.getLong("id"), rs.getString("conversation_id"), rs.getString("sender_id"), rs.getString("body"), rs.getTimestamp("created_at").toInstant());
    }

    /** Returns the buyer's chat for this listing, creating it if needed. Safe when called many times at once. */
    public Conversation findOrCreate(String id, String listingId, String buyerId, String sellerId) {
        db.update("INSERT INTO conversations (id, listing_id, buyer_id, seller_id) VALUES (?,?,?,?) ON CONFLICT (listing_id, buyer_id) DO NOTHING", id, listingId, buyerId, sellerId);
        return db.one("SELECT * FROM conversations WHERE listing_id = ? AND buyer_id = ?", ChatRepo::conv, listingId, buyerId).orElseThrow();
    }

    public Optional<Conversation> get(String id) { return db.one("SELECT * FROM conversations WHERE id = ?", ChatRepo::conv, id); }

    public List<InboxRow> inbox(String userId) {
        return db.list("""
                SELECT c.*, l.title AS ltitle, (SELECT image_key FROM listing_images WHERE listing_id = l.id AND position = 0) AS limg,
                       u.name AS oname, m.body AS lbody, m.created_at AS lat
                FROM conversations c
                JOIN listings l ON l.id = c.listing_id
                JOIN users u ON u.id = CASE WHEN c.buyer_id = ? THEN c.seller_id ELSE c.buyer_id END
                LEFT JOIN LATERAL (SELECT body, created_at FROM messages WHERE conversation_id = c.id ORDER BY id DESC LIMIT 1) m ON true
                WHERE c.buyer_id = ? OR c.seller_id = ?
                ORDER BY coalesce(m.created_at, c.created_at) DESC
                """,
                rs -> new InboxRow(conv(rs), rs.getString("ltitle"), rs.getString("limg"), rs.getString("oname"), rs.getString("lbody"),
                        rs.getTimestamp("lat") == null ? null : rs.getTimestamp("lat").toInstant()),
                userId, userId, userId);
    }

    public List<Message> messages(String conversationId, long afterId) {
        return db.list("SELECT * FROM messages WHERE conversation_id = ? AND id > ? ORDER BY id", ChatRepo::msg, conversationId, afterId);
    }

    /** Stores the message and wakes every app instance listening, so each can push it to its connected browsers. */
    public Message add(String conversationId, String senderId, String body) {
        return db.tx(c -> {
            Message m = Db.list(c, "INSERT INTO messages (conversation_id, sender_id, body) VALUES (?,?,?) RETURNING *", ChatRepo::msg, conversationId, senderId, body).get(0);
            Db.list(c, "SELECT pg_notify(?, ?)", rs -> null, CHANNEL, conversationId);
            return m;
        });
    }
}
