package com.navya;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.navya.config.AppConfig;
import com.navya.exception.InvalidInputException;
import com.navya.storage.LocalFileStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageTest {
    @TempDir Path dir;

    @Test
    @DisplayName("TC-11 local storage: put, get, exists, delete")
    void roundTrip() {
        LocalFileStorage s = new LocalFileStorage(dir);
        s.put("u1/a.jpg", new byte[] {1, 2, 3});
        assertThat(s.exists("u1/a.jpg")).isTrue();
        assertThat(s.get("u1/a.jpg")).contains(new byte[] {1, 2, 3});
        s.delete("u1/a.jpg");
        assertThat(s.exists("u1/a.jpg")).isFalse();
        assertThat(s.get("u1/a.jpg")).isEmpty();
        s.delete("u1/a.jpg"); // deleting a missing file is not an error
    }

    @Test
    @DisplayName("TC-12 storage keys cannot climb out of the folder")
    void traversal() throws Exception {
        LocalFileStorage s = new LocalFileStorage(dir.resolve("files"));
        Files.writeString(dir.resolve("secret.txt"), "x");
        assertThatThrownBy(() -> s.get("../secret.txt")).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> s.put("../../evil", new byte[1])).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("TC-13 config refuses to start without secrets or database settings")
    void config() {
        assertThatThrownBy(() -> AppConfig.fromEnv(Map.of("DB_URL", "jdbc:postgresql://x/y"))).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> AppConfig.fromEnv(Map.of("JWT_SECRET", "x".repeat(32)))).hasMessageContaining("DB_URL");
        assertThatThrownBy(() -> AppConfig.fromEnv(Map.of("JWT_SECRET", "x".repeat(32), "DB_URL", "jdbc:mysql://x"))).hasMessageContaining("DB_URL");
        assertThatThrownBy(() -> AppConfig.fromEnv(Map.of("JWT_SECRET", "x".repeat(32), "DB_URL", "jdbc:postgresql://x/y", "STORAGE", "s3"))).hasMessageContaining("S3_BUCKET");
        assertThatThrownBy(() -> AppConfig.fromEnv(Map.of("JWT_SECRET", "x".repeat(32), "DB_URL", "jdbc:postgresql://x/y", "PORT", "abc"))).hasMessageContaining("PORT");
        AppConfig ok = AppConfig.fromEnv(Map.of("DB_URL", "jdbc:postgresql://x/y", "DEV", "true"));
        assertThat(ok.jwtSecret()).hasSize(64);
        assertThat(ok.boostFeePaise()).isEqualTo(4900);
    }
}
