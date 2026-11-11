package com.navya.worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background queue for outgoing notices (new message, new report). Callers hand over a line and return at once,
 * one worker thread writes it to outbox.log and the app log. There is no email or SMS in this project.
 * If the queue is full the notice is dropped and counted, so a slow disk never slows a request.
 */
public final class Notifier implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(Notifier.class);
    private static final String STOP = "\u0000stop";

    private final BlockingQueue<String> queue;
    private final Thread worker;
    private final Path file;
    private final Clock clock;
    private volatile long dropped;

    public Notifier(Path dir, Clock clock, int capacity) {
        this.file = dir.resolve("outbox.log");
        this.clock = clock;
        this.queue = new ArrayBlockingQueue<>(capacity);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create outbox directory", e);
        }
        this.worker = new Thread(this::run, "navya-notifier");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    public void send(String to, String text) {
        if (!queue.offer(clock.instant() + "\t" + clean(to) + "\t" + clean(text))) dropped++;
    }

    public long dropped() { return dropped; }

    private void run() {
        try {
            while (true) {
                String line = queue.take();
                if (STOP.equals(line)) return;
                log.info("notice {}", line);
                try {
                    Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    log.error("Could not write outbox line", e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Lets the worker finish what is queued, then stops it. */
    @Override
    public void close() {
        try {
            queue.offer(STOP, 1, TimeUnit.SECONDS);
            worker.join(3_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String clean(String s) { return s == null ? "" : s.replaceAll("[\\t\\r\\n]", " "); }
}
