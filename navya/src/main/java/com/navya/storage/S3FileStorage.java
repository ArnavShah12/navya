package com.navya.storage;

import com.navya.exception.InvalidInputException;
import com.navya.exception.PersistenceException;
import java.net.URI;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Object storage in Amazon S3. In AWS the SDK signs in with the ECS task role, so no key exists anywhere in the config.
 * The bucket is created by the infrastructure template. For local runs, S3_ENDPOINT points at MinIO and the usual
 * AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY variables carry its login.
 */
public final class S3FileStorage implements FileStorage {
    private final S3Client s3;
    private final String bucket;

    public S3FileStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    public static S3FileStorage create(String bucket, String region, String endpoint) {
        var b = S3Client.builder()
                .region(Region.of(region))
                .httpClient(UrlConnectionHttpClient.create())
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (endpoint != null) b.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        return new S3FileStorage(b.build(), bucket);
    }

    @Override
    public void put(String key, byte[] data) {
        check(key);
        try {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType(key)).build(), RequestBody.fromBytes(data));
        } catch (RuntimeException e) {
            throw new PersistenceException("Could not store " + key, e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        check(key);
        try {
            return Optional.of(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new PersistenceException("Could not read " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        check(key);
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw new PersistenceException("Could not check " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        check(key);
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException e) {
            throw new PersistenceException("Could not delete " + key, e);
        }
    }

    private static void check(String key) {
        if (key == null || key.isBlank() || key.contains("..") || key.startsWith("/")) throw new InvalidInputException("Invalid storage key");
    }

    private static String contentType(String key) {
        if (key.endsWith(".jpg")) return "image/jpeg";
        if (key.endsWith(".csv")) return "text/csv";
        if (key.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }
}
