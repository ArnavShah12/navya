package com.navya.config;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

/**
 * All settings come from environment variables. Secrets are never read from files in the repo.
 *
 * DB_URL is required (PostgreSQL). STORAGE=local keeps images under DATA_DIR/files, STORAGE=s3 uses S3
 * (S3_BUCKET, S3_REGION; S3_ENDPOINT only for a local emulator such as MinIO). In AWS the SDK signs in with
 * the task role, so there are no access keys in the configuration.
 */
public record AppConfig(
        int port,
        Path dataDir,
        String jwtSecret,
        boolean dev,
        String dbUrl,
        String dbUser,
        String dbPassword,
        String storageType,
        String s3Bucket,
        String s3Region,
        String s3Endpoint,
        long boostFeePaise,
        int boostDays,
        int listingLifeDays) {

    public static AppConfig fromEnv(Map<String, String> env) {
        boolean dev = "true".equalsIgnoreCase(env.getOrDefault("DEV", "false"));
        String jwt = env.get("JWT_SECRET");
        if (jwt == null || jwt.length() < 32) {
            if (!dev) throw new IllegalStateException("JWT_SECRET must be set to at least 32 characters (or set DEV=true for local runs)");
            jwt = randomSecret();
        }
        String dbUrl = blankToNull(env.get("DB_URL"));
        if (dbUrl == null || !dbUrl.startsWith("jdbc:postgresql:")) throw new IllegalStateException("DB_URL must be set and start with jdbc:postgresql:");
        String storage = env.getOrDefault("STORAGE", "local").toLowerCase();
        if (!storage.equals("local") && !storage.equals("s3")) throw new IllegalStateException("STORAGE must be local or s3, got: " + storage);
        String bucket = blankToNull(env.get("S3_BUCKET"));
        if (storage.equals("s3") && bucket == null) throw new IllegalStateException("STORAGE=s3 needs S3_BUCKET");
        return new AppConfig(
                intOf(env, "PORT", 8080),
                Path.of(env.getOrDefault("DATA_DIR", "./data")),
                jwt,
                dev,
                dbUrl,
                blankToNull(env.get("DB_USER")),
                blankToNull(env.get("DB_PASSWORD")),
                storage,
                bucket,
                env.getOrDefault("S3_REGION", "ap-south-1"),
                blankToNull(env.get("S3_ENDPOINT")),
                intOf(env, "BOOST_FEE_PAISE", 4900),
                intOf(env, "BOOST_DAYS", 7),
                intOf(env, "LISTING_LIFE_DAYS", 60));
    }

    /** Test configuration: local file storage in the given folder, database given by the caller. */
    public static AppConfig forTest(Path dataDir, String jdbcUrl, String user, String password) {
        return new AppConfig(0, dataDir, randomSecret(), true, jdbcUrl, user, password, "local", null, "ap-south-1", null, 4900, 7, 60);
    }

    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }

    private static int intOf(Map<String, String> env, String key, int def) {
        String v = env.get(key);
        if (v == null || v.isBlank()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(key + " must be a number, got: " + v);
        }
    }

    private static String randomSecret() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
