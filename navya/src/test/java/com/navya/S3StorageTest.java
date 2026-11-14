package com.navya;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.navya.exception.InvalidInputException;
import com.navya.storage.S3FileStorage;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Runs only when S3_TEST_ENDPOINT is set (CI starts MinIO as a service and sets it, with AWS_ACCESS_KEY_ID and
 * AWS_SECRET_ACCESS_KEY for the emulator). Skipped on a laptop without one.
 */
@EnabledIfEnvironmentVariable(named = "S3_TEST_ENDPOINT", matches = ".+")
class S3StorageTest {
    @Test
    @DisplayName("TC-67 S3 storage: put, get, exists, delete, bad keys (against MinIO)")
    void roundTrip() {
        String endpoint = System.getenv("S3_TEST_ENDPOINT"), bucket = "navya-test-" + System.nanoTime();
        try (S3Client admin = S3Client.builder().region(Region.of("ap-south-1")).endpointOverride(URI.create(endpoint)).forcePathStyle(true).build()) {
            admin.createBucket(b -> b.bucket(bucket));
            S3FileStorage s = S3FileStorage.create(bucket, "ap-south-1", endpoint);
            s.put("u1/a.jpg", new byte[] {1, 2, 3});
            assertThat(s.exists("u1/a.jpg")).isTrue();
            assertThat(s.get("u1/a.jpg")).contains(new byte[] {1, 2, 3});
            assertThat(s.get("u1/missing.jpg")).isEmpty();
            assertThat(s.exists("u1/missing.jpg")).isFalse();
            s.delete("u1/a.jpg");
            assertThat(s.exists("u1/a.jpg")).isFalse();
            assertThatThrownBy(() -> s.get("../x")).isInstanceOf(InvalidInputException.class);
            assertThatThrownBy(() -> s.put("/abs", new byte[1])).isInstanceOf(InvalidInputException.class);
        }
    }
}
