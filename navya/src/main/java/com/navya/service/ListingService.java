package com.navya.service;

import com.navya.domain.Category;
import com.navya.domain.Condition;
import com.navya.domain.Listing;
import com.navya.domain.Money;
import com.navya.exception.ForbiddenException;
import com.navya.exception.InvalidInputException;
import com.navya.exception.ListingNotAvailableException;
import com.navya.exception.NotFoundException;
import com.navya.exception.TooManyImagesException;
import com.navya.repo.ListingRepo;
import com.navya.storage.FileStorage;
import com.navya.util.Ids;
import com.navya.util.Validate;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Listings, saved items and photos. Every write checks who is asking. */
public final class ListingService {
    public static final int MAX_IMAGES = 4;
    public static final int MAX_IMAGE_BYTES = 600_000;
    public static final int PAGE_SIZE = 24;

    /** What the seller types in. Price is in whole rupees. */
    public record Input(String title, Long price, String category, String condition, String size, String city, String description, List<String> images) {}

    /** Query-string values from the home page. */
    public record Query(String q, String category, String city, Long min, Long max, boolean favs, String sort, int page) {}

    private final ListingRepo repo;
    private final FileStorage storage;
    private final AuditLog audit;
    private final Clock clock;
    private final Metrics metrics;

    public ListingService(ListingRepo repo, FileStorage storage, AuditLog audit, Clock clock, Metrics metrics) {
        this.repo = repo;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
        this.metrics = metrics;
    }

    public Listing create(Principal p, Input in) {
        Listing l = build(Ids.newId(), p.userId(), in, p);
        repo.insert(l);
        metrics.listingsCreated.incrementAndGet();
        return repo.get(l.id()).orElseThrow();
    }

    public Listing update(Principal p, String id, Input in) {
        Listing old = owned(p, id);
        if (!Listing.AVAILABLE.equals(old.status()) && !Listing.SOLD.equals(old.status())) throw new ListingNotAvailableException("This listing can no longer be edited");
        Listing l = build(id, old.sellerId(), in, p);
        repo.update(l);
        old.images().stream().filter(k -> !l.images().contains(k)).forEach(this::quietDelete);
        return repo.get(id).orElseThrow();
    }

    private Listing build(String id, String sellerId, Input in, Principal p) {
        Validate.required(in, "Listing");
        List<String> images = in.images() == null ? List.of() : in.images();
        if (images.size() > MAX_IMAGES) throw new TooManyImagesException("A listing can have at most " + MAX_IMAGES + " photos");
        for (String k : images) {
            // a seller can only attach photos that live in their own folder
            if (k == null || !k.startsWith(sellerId + "/") || k.contains("..") || !storage.exists(k)) throw new InvalidInputException("Photo not found, upload it again");
        }
        long rupees = Validate.required(in.price(), "Price");
        return new Listing(id, sellerId, Validate.text(in.title(), "Title", 1, 80), Money.toPaise(rupees), Category.parse(in.category()).label,
                Condition.parse(in.condition()).label, Validate.optionalText(in.size(), "Size", 20), Validate.text(in.city(), "City", 1, 60),
                Validate.text(in.description(), "Description", 1, 2000), List.copyOf(images), Listing.AVAILABLE, null, clock.instant());
    }

    /** A removed or expired listing is only visible to its seller and the Navya team. */
    public Listing get(Principal viewer, String id) {
        Listing l = Ids.valid(id) ? repo.get(id).orElse(null) : null;
        if (l == null) throw new NotFoundException("Listing not found");
        boolean live = Listing.AVAILABLE.equals(l.status()) || Listing.SOLD.equals(l.status());
        if (!live && (viewer == null || (!viewer.isAdmin() && !viewer.userId().equals(l.sellerId())))) throw new NotFoundException("Listing not found");
        return l;
    }

    public ListingRepo.Page search(Principal viewer, Query q) {
        if (q.favs() && viewer == null) throw new ForbiddenException("Log in to see saved items");
        List<String> words = q.q() == null || q.q().isBlank() ? List.of() : Arrays.stream(q.q().toLowerCase().trim().split("\\s+")).limit(8).toList();
        Long min = q.min() == null ? null : Money.toPaise(q.min());
        Long max = q.max() == null ? null : Money.toPaise(q.max());
        int page = Math.max(0, q.page());
        return repo.search(new ListingRepo.Filter(words, q.category(), q.city(), min, max, q.favs() ? viewer.userId() : null, null, false, q.sort(), PAGE_SIZE, page * PAGE_SIZE), clock.instant());
    }

    public ListingRepo.Page bySeller(String sellerId) {
        if (!Ids.valid(sellerId)) throw new NotFoundException("Seller not found");
        return repo.search(new ListingRepo.Filter(List.of(), null, null, null, null, null, sellerId, false, "new", 100, 0), clock.instant());
    }

    /** The seller's own listings in every status, newest first. */
    public List<Listing> mine(Principal p) {
        return repo.search(new ListingRepo.Filter(List.of(), null, null, null, null, null, p.userId(), true, "new", 200, 0), clock.instant()).items();
    }

    public Listing setStatus(Principal p, String id, String status) {
        Listing l = owned(p, id);
        if (!Set.of(Listing.AVAILABLE, Listing.SOLD).contains(status)) throw new InvalidInputException("Status must be available or sold");
        if (!Listing.AVAILABLE.equals(l.status()) && !Listing.SOLD.equals(l.status())) throw new ListingNotAvailableException("This listing can no longer change status");
        repo.setStatus(id, status);
        return repo.get(id).orElseThrow();
    }

    public void delete(Principal p, String id) {
        Listing l = Ids.valid(id) ? repo.get(id).orElse(null) : null;
        if (l == null) throw new NotFoundException("Listing not found");
        if (!p.isAdmin() && !p.userId().equals(l.sellerId())) throw new ForbiddenException("This is not your listing");
        repo.delete(id);
        l.images().forEach(this::quietDelete);
        if (p.isAdmin()) audit.record(p.userId(), "listing.delete", id);
    }

    private Listing owned(Principal p, String id) {
        Listing l = Ids.valid(id) ? repo.get(id).orElse(null) : null;
        if (l == null) throw new NotFoundException("Listing not found");
        if (!p.userId().equals(l.sellerId())) throw new ForbiddenException("This is not your listing");
        return l;
    }

    // ---- saved items ----

    /** Returns true when the listing is now saved. */
    public boolean toggleFavourite(Principal p, String listingId) {
        get(p, listingId);
        return repo.toggleFavourite(p.userId(), listingId);
    }

    public Set<String> favouriteIds(Principal p) { return repo.favouriteIds(p.userId()); }

    // ---- photos ----

    /** Stores one JPEG. Size and file signature are checked here, not trusted from the browser. */
    public String saveImage(Principal p, byte[] data) {
        if (data == null || data.length == 0) throw new InvalidInputException("No image received");
        if (data.length > MAX_IMAGE_BYTES) throw new InvalidInputException("Photo is too large (max " + MAX_IMAGE_BYTES / 1000 + " KB)");
        boolean jpeg = data.length > 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF;
        if (!jpeg) throw new InvalidInputException("Photo must be a JPEG");
        String key = p.userId() + "/" + Ids.newId() + ".jpg";
        storage.put(key, data);
        return key;
    }

    public Optional<byte[]> image(String key) {
        if (key == null || key.contains("..") || !key.endsWith(".jpg")) return Optional.empty();
        return storage.get(key);
    }

    private void quietDelete(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            // an orphan photo costs a few KB, a failed delete must not fail the request
        }
    }

    /** Expires old listings. Called by the reaper thread. */
    public int expireOld(int lifeDays) {
        int n = repo.expireOlderThan(clock.instant().minus(java.time.Duration.ofDays(lifeDays)));
        if (n > 0) metrics.listingsExpired.addAndGet(n);
        return n;
    }

}
