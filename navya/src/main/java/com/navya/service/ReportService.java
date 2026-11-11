package com.navya.service;

import com.navya.domain.Listing;
import com.navya.domain.Report;
import com.navya.exception.InvalidInputException;
import com.navya.exception.NotFoundException;
import com.navya.exception.SelfChatException;
import com.navya.repo.ListingRepo;
import com.navya.repo.ReportRepo;
import com.navya.util.Ids;
import com.navya.util.Validate;
import com.navya.worker.Notifier;
import java.time.Clock;
import java.util.List;

/** Members report listings, the Navya team reviews them. */
public final class ReportService {
    private final ReportRepo reports;
    private final ListingRepo listings;
    private final AuditLog audit;
    private final Notifier notifier;
    private final Clock clock;

    public ReportService(ReportRepo reports, ListingRepo listings, AuditLog audit, Notifier notifier, Clock clock) {
        this.reports = reports;
        this.listings = listings;
        this.audit = audit;
        this.notifier = notifier;
        this.clock = clock;
    }

    public void report(Principal p, String listingId, String reason) {
        Listing l = Ids.valid(listingId) ? listings.get(listingId).orElse(null) : null;
        if (l == null) throw new NotFoundException("Listing not found");
        if (l.sellerId().equals(p.userId())) throw new SelfChatException("You cannot report your own listing");
        reports.insert(new Report(Ids.newId(), listingId, p.userId(), Validate.text(reason, "Reason", 1, 500), "open", clock.instant()));
        notifier.send("admin", "listing " + listingId + " reported");
    }

    public List<ReportRepo.Row> open(Principal admin) {
        admin.requireAdmin();
        return reports.open();
    }

    /** action is "remove" (takes the listing down) or "dismiss". */
    public void resolve(Principal admin, String reportId, String action) {
        admin.requireAdmin();
        Report r = Ids.valid(reportId) ? reports.get(reportId).orElse(null) : null;
        if (r == null) throw new NotFoundException("Report not found");
        switch (action == null ? "" : action) {
            case "remove" -> {
                listings.setStatus(r.listingId(), Listing.REMOVED);
                reports.setStatus(reportId, "actioned");
            }
            case "dismiss" -> reports.setStatus(reportId, "dismissed");
            default -> throw new InvalidInputException("Action must be remove or dismiss");
        }
        audit.record(admin.userId(), "report." + action, reportId);
    }
}
