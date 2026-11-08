package com.navya.repo;

import com.navya.exception.PersistenceException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Connection pool, schema set-up and the few JDBC helpers every repository uses. Values are always bound, never concatenated. */
public final class Db implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(Db.class);
    private final HikariDataSource ds;

    @FunctionalInterface
    public interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }

    private Db(HikariDataSource ds) { this.ds = ds; }

    public static Db open(String url, String user, String password, int poolSize) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        if (user != null) cfg.setUsername(user);
        if (password != null) cfg.setPassword(password);
        cfg.setMaximumPoolSize(poolSize);
        cfg.setConnectionTimeout(10_000);
        cfg.setPoolName("navya-db");
        try {
            return new Db(new HikariDataSource(cfg));
        } catch (RuntimeException e) {
            throw new PersistenceException("Cannot connect to the database", e);
        }
    }

    public DataSource dataSource() { return ds; }

    /** Runs db/schema.sql. An advisory lock makes two instances starting together take turns. */
    public void migrate() {
        String sql;
        try (InputStream in = Db.class.getResourceAsStream("/db/schema.sql")) {
            if (in == null) throw new PersistenceException("db/schema.sql is missing from the jar");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PersistenceException("Cannot read db/schema.sql", e);
        }
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT pg_advisory_xact_lock(727274)");
                st.execute(sql);
            }
            c.commit();
            log.info("Database schema is up to date");
        } catch (SQLException e) {
            throw new PersistenceException("Schema migration failed", e);
        }
    }

    public boolean ping() {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            return st.execute("SELECT 1");
        } catch (SQLException e) {
            return false;
        }
    }

    public <T> List<T> list(String sql, RowMapper<T> m, Object... args) {
        try (Connection c = ds.getConnection()) {
            return list(c, sql, m, args);
        } catch (SQLException e) {
            throw new PersistenceException("Database error", e);
        }
    }

    public <T> Optional<T> one(String sql, RowMapper<T> m, Object... args) {
        List<T> l = list(sql, m, args);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    /** Returns the number of rows changed. */
    public int update(String sql, Object... args) {
        try (Connection c = ds.getConnection()) {
            return update(c, sql, args);
        } catch (SQLException e) {
            throw new PersistenceException("Database error", e);
        }
    }

    /** True when the exception is a unique-constraint violation (SQLSTATE 23505). */
    public static boolean isDuplicate(RuntimeException e) {
        for (Throwable t = e; t != null; t = t.getCause()) if (t instanceof SQLException s && "23505".equals(s.getSQLState())) return true;
        return false;
    }

    @FunctionalInterface
    public interface Work<T> { T run(Connection c) throws SQLException; }

    /** Runs the work in one transaction. */
    public <T> T tx(Work<T> w) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                T r = w.run(c);
                c.commit();
                return r;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Database error", e);
        }
    }

    public static <T> List<T> list(Connection c, String sql, RowMapper<T> m, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(m.map(rs));
                return out;
            }
        }
    }

    public static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a instanceof Instant t) ps.setTimestamp(i + 1, Timestamp.from(t));
            else ps.setObject(i + 1, a);
        }
    }

    @Override
    public void close() { ds.close(); }
}
