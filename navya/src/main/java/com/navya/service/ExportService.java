package com.navya.service;

import com.navya.domain.Listing;
import com.navya.repo.ListingRepo;
import com.navya.storage.FileStorage;
import com.navya.util.Json;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

/** Writes the listing catalogue to files: CSV for admins to open in a spreadsheet, JSON as a backup copy. Both land in file storage (S3 in the cloud). */
public final class ExportService {
    private final ListingRepo listings;
    private final FileStorage storage;
    private final Clock clock;

    public ExportService(ListingRepo listings, FileStorage storage, Clock clock) {
        this.listings = listings;
        this.storage = storage;
        this.clock = clock;
    }

    private List<Listing> all() {
        return listings.search(new ListingRepo.Filter(List.of(), null, null, null, null, null, null, false, "new", 5000, 0), clock.instant()).items();
    }

    public String csv() {
        StringBuilder sb = new StringBuilder("id,title,price_rupees,category,condition,size,city,status,created_at\n");
        for (Listing l : all()) {
            sb.append(cell(l.id())).append(',').append(cell(l.title())).append(',').append(l.pricePaise() / 100).append(',').append(cell(l.category())).append(',')
                    .append(cell(l.condition())).append(',').append(cell(l.size())).append(',').append(cell(l.city())).append(',').append(cell(l.status())).append(',')
                    .append(l.createdAt()).append('\n');
        }
        return sb.toString();
    }

    /** Writes exports/listings-<time>.csv and .json to storage and returns the CSV key. */
    public String exportToStorage() {
        String stamp = clock.instant().toString().replace(':', '-');
        String csvKey = "exports/listings-" + stamp + ".csv";
        storage.put(csvKey, csv().getBytes(StandardCharsets.UTF_8));
        try {
            storage.put("exports/listings-" + stamp + ".json", Json.mapper().writeValueAsBytes(all()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new com.navya.exception.PersistenceException("Could not write JSON export", e);
        }
        return csvKey;
    }

    /** Quotes the cell, and defuses a leading = + - @ so a spreadsheet does not run seller-written text as a formula. */
    static String cell(String s) {
        String v = s == null ? "" : s;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
