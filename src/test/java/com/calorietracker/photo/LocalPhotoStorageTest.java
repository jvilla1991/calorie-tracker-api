package com.calorietracker.photo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link LocalPhotoStorage} against a temporary directory. */
class LocalPhotoStorageTest {

    private static final String KEY = "meal-photos/2026-10-16/7/abc-full.jpg";

    @TempDir Path dir;
    private LocalPhotoStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalPhotoStorage(dir);
    }

    @Test
    void put_writesTheFileUnderTheKeyPath_andReadReturnsTheBytes() throws Exception {
        byte[] bytes = TestPhotos.jpeg(100);
        storage.put(KEY, bytes, "image/jpeg");

        assertThat(Files.readAllBytes(dir.resolve(KEY))).isEqualTo(bytes);
        assertThat(storage.read(KEY)).hasValueSatisfying(b -> assertThat(b).isEqualTo(bytes));
    }

    @Test
    void put_replacesAnExistingFile() {
        storage.put(KEY, TestPhotos.jpeg(100), "image/jpeg");
        byte[] second = TestPhotos.jpeg(50);
        storage.put(KEY, second, "image/jpeg");
        assertThat(storage.read(KEY)).hasValueSatisfying(b -> assertThat(b).isEqualTo(second));
    }

    @Test
    void read_missing_isEmpty() {
        assertThat(storage.read("meal-photos/nope.jpg")).isEmpty();
    }

    @Test
    void delete_removesTheFile_andAMissingKeyIsFine() {
        storage.put(KEY, TestPhotos.jpeg(100), "image/jpeg");
        storage.delete(KEY);
        assertThat(Files.exists(dir.resolve(KEY))).isFalse();
        assertThat(storage.read(KEY)).isEmpty();

        storage.delete(KEY); // again: no error
        storage.delete("meal-photos/never-existed.jpg");
    }

    @Test
    void servesBytesItself_soThereIsNoPresignedUrl() {
        assertThat(storage.presignedGetUrl(KEY, Duration.ofMinutes(10))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.jpg", "meal-photos/../../outside.jpg", "", "."})
    void keysOutsideTheRootDirectory_areRefused(String key) {
        assertThatThrownBy(() -> storage.put(key, TestPhotos.jpeg(10), "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.read(key)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(IllegalArgumentException.class);
    }
}
