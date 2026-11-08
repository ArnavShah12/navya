package com.navya.repo;

import com.navya.domain.Report;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class ReportRepo {
    public record Row(Report report, String listingTitle, String listingStatus) {}

    private final Db db;

    public ReportRepo(Db db) { this.db = db; }

    private static Report map(ResultSet rs) throws SQLException {
        return new Report(rs.getString("id"), rs.getString("listing_id"), rs.getString("reporter_id"), rs.getString("reason"), rs.getString("status"), rs.getTimestamp("created_at").toInstant());
    }

    /** One report per user and listing. A second one from the same user is ignored. */
    public void insert(Report r) {
        db.update("INSERT INTO reports (id, listing_id, reporter_id, reason) VALUES (?,?,?,?) ON CONFLICT (listing_id, reporter_id) DO NOTHING", r.id(), r.listingId(), r.reporterId(), r.reason());
    }

    public List<Row> open() {
        return db.list("SELECT r.*, l.title AS ltitle, l.status AS lstatus FROM reports r JOIN listings l ON l.id = r.listing_id WHERE r.status = 'open' ORDER BY r.created_at",
                rs -> new Row(map(rs), rs.getString("ltitle"), rs.getString("lstatus")));
    }

    public Optional<Report> get(String id) { return db.one("SELECT * FROM reports WHERE id = ?", ReportRepo::map, id); }

    public void setStatus(String id, String status) { db.update("UPDATE reports SET status = ? WHERE id = ?", status, id); }
}
