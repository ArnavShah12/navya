package com.navya.storage;

import java.util.Optional;

/**
 * Where binary files live: listing photos, CSV and JSON exports.
 * LocalFileStorage writes to a folder. The S3 version plugs in here.
 */
public interface FileStorage {
    void put(String key, byte[] data);

    Optional<byte[]> get(String key);

    boolean exists(String key);

    /** Removes the file. Missing keys are ignored. */
    void delete(String key);
}
