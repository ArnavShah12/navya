package com.navya.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Append-only record of privileged actions. One line each: time, actor, action, target. */
public final class AuditLog {
    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);
    private final Path file;
    private final Clock clock;

    public AuditLog(Path dir, Clock clock) {
        this.file = dir.resolve("audit.log");
        this.clock = clock;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create audit directory", e);
        }
    }

    public synchronized void record(String actor, String action, String target) {
        log.info("audit actor={} action={} target={}", clean(actor), clean(action), clean(target));
        String line = clock.instant() + "\t" + clean(actor) + "\t" + clean(action) + "\t" + clean(target) + System.lineSeparator();
        try {
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Could not write audit line: {}", line.trim(), e);
        }
    }

    public synchronized List<String> tail(int max) {
        try {
            if (!Files.exists(file)) return List.of();
            List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
            return all.subList(Math.max(0, all.size() - max), all.size());
        } catch (IOException e) {
            log.error("Could not read audit log", e);
            return List.of();
        }
    }

    private static String clean(String s) { return s == null ? "" : s.replaceAll("[\\t\\r\\n]", " "); }
}
