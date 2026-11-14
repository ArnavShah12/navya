package com.navya;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/** Starts a throwaway PostgreSQL for hand testing without Docker. Run it, then start the app with DB_URL printed below. */
public final class DevPostgres {
    public static void main(String[] args) throws Exception {
        EmbeddedPostgres pg = EmbeddedPostgres.builder().setPort(Integer.parseInt(args.length > 0 ? args[0] : "5433")).start();
        System.out.println("DB_URL=" + pg.getJdbcUrl("postgres", "postgres"));
        Thread.currentThread().join();
    }
}
