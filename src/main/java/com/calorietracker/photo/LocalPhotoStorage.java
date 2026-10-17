package com.calorietracker.photo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Photos as plain files under a root directory (local development and tests, so they never need
 * AWS). A key's slashes become subdirectories. The API serves the bytes itself.
 */
public class LocalPhotoStorage implements PhotoStorage {

    private final Path root;

    public LocalPhotoStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        Path file = resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store photo file " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete photo file " + key, e);
        }
    }

    /** Not used locally: photos are served from {@link #read}. */
    @Override
    public Optional<URI> presignedGetUrl(String key, Duration ttl) {
        return Optional.empty();
    }

    @Override
    public Optional<byte[]> read(String key) {
        try {
            return Optional.of(Files.readAllBytes(resolve(key)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read photo file " + key, e);
        }
    }

    /** The file for {@code key}, refusing any key that would point outside the root directory. */
    private Path resolve(String key) {
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root) || file.equals(root)) {
            throw new IllegalArgumentException("Invalid photo storage key: " + key);
        }
        return file;
    }
}
