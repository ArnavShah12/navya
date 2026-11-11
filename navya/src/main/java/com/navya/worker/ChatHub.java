package com.navya.worker;

import com.navya.repo.ChatRepo;
import io.javalin.websocket.WsContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pushes "new message" pings to browsers that have a chat open.
 * Each app instance keeps its own WebSocket sessions. A message saved on instance A is announced through
 * PostgreSQL NOTIFY, and a listener thread on every instance (this class) hears it and pings its own sessions.
 * The ping carries no message text: the browser then asks the normal API for what is new.
 * shortcut: one LISTEN connection per instance, taken from the pool for good. Fine for a few instances,
 * and it must not go through a connection pooler in transaction mode (such as RDS Proxy).
 */
public final class ChatHub implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ChatHub.class);

    private final DataSource ds;
    private final ConcurrentHashMap<String, Set<WsContext>> sessions = new ConcurrentHashMap<>();
    private final Thread listener;
    private volatile boolean running = true;

    public ChatHub(DataSource ds) {
        this.ds = ds;
        this.listener = new Thread(this::listen, "navya-chat-listener");
        this.listener.setDaemon(true);
    }

    public void start() { listener.start(); }

    public void join(String conversationId, WsContext ctx) {
        sessions.computeIfAbsent(conversationId, k -> ConcurrentHashMap.newKeySet()).add(ctx);
    }

    public void leave(String conversationId, WsContext ctx) {
        Set<WsContext> s = sessions.get(conversationId);
        if (s != null) {
            s.remove(ctx);
            if (s.isEmpty()) sessions.remove(conversationId, s);
        }
    }

    public int sessionCount() { return sessions.values().stream().mapToInt(Set::size).sum(); }

    /** Pings every local session in this conversation. Also called directly after a send, so the sender's other tabs update at once. */
    public void ping(String conversationId) {
        Set<WsContext> s = sessions.get(conversationId);
        if (s == null) return;
        for (WsContext ctx : s) {
            try {
                if (ctx.session.isOpen()) ctx.send("{\"type\":\"new\"}");
            } catch (RuntimeException e) {
                log.debug("ping failed, dropping session", e);
                s.remove(ctx);
            }
        }
    }

    private void listen() {
        while (running) {
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                st.execute("LISTEN " + ChatRepo.CHANNEL);
                PGConnection pg = c.unwrap(PGConnection.class);
                log.info("Chat listener is running");
                while (running) {
                    PGNotification[] ns = pg.getNotifications(1000);
                    if (ns == null) continue;
                    for (PGNotification n : ns) ping(n.getParameter());
                }
            } catch (SQLException | RuntimeException e) {
                if (!running) return;
                log.warn("Chat listener lost its connection, retrying in 2 s: {}", e.getMessage());
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    @Override
    public void close() {
        running = false;
        listener.interrupt();
    }
}
