package com.navya.storage;

import com.navya.exception.InvalidInputException;
import com.navya.exception.PersistenceException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class LocalFileStorage implements FileStorage {
    private final Path root;

    public LocalFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new PersistenceException("Cannot create storage folder " + root, e);
        }
    }

    @Override
    public void put(String key, byte[] data) {
        Path p = resolve(key);
        try {
            Files.createDirectories(p.getParent());
            Files.write(p, data);
        } catch (IOException e) {
            throw new PersistenceException("Could not store " + key, e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path p = resolve(key);
        try {
            return Files.exists(p) ? Optional.of(Files.readAllBytes(p)) : Optional.empty();
        } catch (IOException e) {
            throw new PersistenceException("Could not read " + key, e);
        }
    }

    @Override
    public boolean exists(String key) { return Files.exists(resolve(key)); }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new PersistenceException("Could not delete " + key, e);
        }
    }

    /** Keys may never climb out of the storage folder. */
    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new InvalidInputException("Invalid storage key");
        return p;
    }
}
