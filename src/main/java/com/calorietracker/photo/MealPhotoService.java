package com.calorietracker.photo;

import com.calorietracker.diary.DiaryMeal;
import com.calorietracker.diary.DiaryMealRepository;
import com.calorietracker.photo.dto.MealPhotoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Photos attached to diary meals: upload, view, delete, and the per-meal lists the diary shows.
 * The phone sends each photo twice, already shrunk and re-encoded as JPEG: a full-size image and
 * a thumbnail. Files go to {@link PhotoStorage}; the {@code meal_photos} row records their keys.
 * <p>
 * When no storage is configured (prod without {@code MEDIA_BUCKET}, see
 * {@link PhotoStorageConfig}), upload, view and delete answer 503; the diary still works.
 */
@Service
public class MealPhotoService {

    private static final Logger log = LoggerFactory.getLogger(MealPhotoService.class);

    public static final int MAX_PHOTOS_PER_MEAL = 6;
    public static final long FULL_MAX_BYTES = DataSize.ofMegabytes(3).toBytes();
    public static final long THUMB_MAX_BYTES = DataSize.ofKilobytes(500).toBytes();
    /** Largest accepted {@code width}/{@code height} (the phone sends at most 1600). */
    public static final int MAX_DIMENSION = 10_000;
    /** How long a presigned photo URL stays valid. */
    public static final Duration PRESIGNED_URL_TTL = Duration.ofMinutes(10);
    public static final String NOT_CONFIGURED = "Photo storage not configured";

    static final String JPEG = "image/jpeg";

    /** Which of a photo's two files to serve. */
    public enum Variant { FULL, THUMB }

    /** How to answer a photo request: redirect to a short-lived URL (S3), or send the JPEG bytes. */
    public sealed interface PhotoContent {
        record Redirect(URI url) implements PhotoContent {}
        record Jpeg(byte[] bytes) implements PhotoContent {}
    }

    private final MealPhotoRepository photoRepo;
    private final DiaryMealRepository mealRepo;
    private final PhotoStorage storage; // null when not configured

    public MealPhotoService(MealPhotoRepository photoRepo, DiaryMealRepository mealRepo,
                            Optional<PhotoStorage> storage) {
        this.photoRepo = photoRepo;
        this.mealRepo = mealRepo;
        this.storage = storage.orElse(null);
        if (this.storage == null) {
            log.warn("No meal photo storage is configured (set MEDIA_BUCKET, i.e. app.media.bucket). "
                    + "Photo upload, view and delete will answer 503 until it is; everything else works.");
        }
    }

    /**
     * Attaches a photo to a meal. {@code width}/{@code height} (optional) describe the full image.
     * Both files are stored before the row is written; if writing the row fails, or the
     * transaction later rolls back, the stored files are deleted again.
     *
     * @throws ResponseStatusException 503 if no storage is configured; 400 if a part is missing or
     *     a dimension is outside 1-{@value #MAX_DIMENSION}; 413 if {@code full} is over 3 MB or
     *     {@code thumb} over 500 KB; 415 if a part is not a JPEG; 404 if no such meal; 409 if the
     *     meal already has {@value #MAX_PHOTOS_PER_MEAL} photos
     */
    @Transactional
    public MealPhotoResponse upload(Long mealId, MultipartFile full, MultipartFile thumb,
                                    Integer width, Integer height) {
        PhotoStorage storage = requireStorage();
        if (full == null || full.isEmpty() || thumb == null || thumb.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Both 'full' and 'thumb' JPEG parts are required");
        }
        byte[] fullBytes = readJpeg("full", full, FULL_MAX_BYTES, "3 MB");
        byte[] thumbBytes = readJpeg("thumb", thumb, THUMB_MAX_BYTES, "500 KB");
        checkDimension("width", width);
        checkDimension("height", height);

        // Locks the meal row: concurrent uploads to one meal take turns, so the limit holds.
        DiaryMeal meal = mealRepo.findByIdForUpdate(mealId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found"));
        if (photoRepo.countByDiaryMealId(mealId) >= MAX_PHOTOS_PER_MEAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A meal can have at most " + MAX_PHOTOS_PER_MEAL + " photos");
        }

        String base = "meal-photos/" + meal.getDiaryEntry().getEntryDate() + "/" + mealId + "/" + UUID.randomUUID();
        String fullKey = base + "-full.jpg";
        String thumbKey = base + "-thumb.jpg";

        storage.put(fullKey, fullBytes, JPEG);
        try {
            storage.put(thumbKey, thumbBytes, JPEG);
            MealPhoto photo = new MealPhoto();
            photo.setDiaryMealId(mealId);
            photo.setFullKey(fullKey);
            photo.setThumbKey(thumbKey);
            photo.setWidth(width);
            photo.setHeight(height);
            photo.setBytes(fullBytes.length);
            photo = photoRepo.saveAndFlush(photo);
            deleteFilesIfRolledBack(storage, fullKey, thumbKey);
            return MealPhotoResponse.of(photo.getId());
        } catch (RuntimeException e) {
            deleteQuietly(storage, fullKey, thumbKey);
            throw e;
        }
    }

    /**
     * How to deliver one of a photo's files: a redirect to a presigned URL valid for
     * {@link #PRESIGNED_URL_TTL} (S3), or the JPEG bytes (local storage).
     *
     * @throws ResponseStatusException 503 if no storage is configured; 404 if no such photo (or,
     *                                 for local storage, its file is missing)
     */
    @Transactional(readOnly = true)
    public PhotoContent load(Long photoId, Variant variant) {
        PhotoStorage storage = requireStorage();
        MealPhoto photo = photoRepo.findById(photoId).orElseThrow(MealPhotoService::photoNotFound);
        String key = variant == Variant.FULL ? photo.getFullKey() : photo.getThumbKey();
        Optional<URI> url = storage.presignedGetUrl(key, PRESIGNED_URL_TTL);
        if (url.isPresent()) {
            return new PhotoContent.Redirect(url.get());
        }
        return storage.read(key).<PhotoContent>map(PhotoContent.Jpeg::new)
                .orElseThrow(MealPhotoService::photoNotFound);
    }

    /**
     * Deletes both files (a file that is already gone is fine), then the row.
     *
     * @throws ResponseStatusException 503 if no storage is configured; 404 if no such photo
     */
    @Transactional
    public void delete(Long photoId) {
        PhotoStorage storage = requireStorage();
        MealPhoto photo = photoRepo.findById(photoId).orElseThrow(MealPhotoService::photoNotFound);
        storage.delete(photo.getFullKey());
        storage.delete(photo.getThumbKey());
        photoRepo.delete(photo);
    }

    /**
     * The photos of every meal on {@code date}, keyed by meal id, each list oldest first. One
     * query for the whole day. Needs no storage, so the diary works even when it is not configured.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<MealPhotoResponse>> byMealForDay(LocalDate date) {
        Map<Long, List<MealPhotoResponse>> byMeal = new HashMap<>();
        for (MealPhoto p : photoRepo.findAllForDate(date)) {
            byMeal.computeIfAbsent(p.getDiaryMealId(), id -> new ArrayList<>()).add(MealPhotoResponse.of(p.getId()));
        }
        return byMeal;
    }

    /** One meal's photos, oldest first. */
    @Transactional(readOnly = true)
    public List<MealPhotoResponse> forMeal(Long mealId) {
        return photoRepo.findByDiaryMealIdOrderByIdAsc(mealId).stream()
                .map(p -> MealPhotoResponse.of(p.getId()))
                .toList();
    }

    // --- helpers ---------------------------------------------------------------------------

    private PhotoStorage requireStorage() {
        if (storage == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, NOT_CONFIGURED);
        }
        return storage;
    }

    private static byte[] readJpeg(String part, MultipartFile file, long maxBytes, String maxLabel) {
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
                    "'" + part + "' must be at most " + maxLabel);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read '" + part + "'", e);
        }
        if (!isJpeg(bytes)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "'" + part + "' must be a JPEG image");
        }
        return bytes;
    }

    /** Every JPEG file starts with the bytes FF D8 FF (start-of-image, then the next marker). */
    static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private static void checkDimension(String name, Integer value) {
        if (value != null && (value < 1 || value > MAX_DIMENSION)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'" + name + "' must be 1-" + MAX_DIMENSION);
        }
    }

    /** If the surrounding transaction rolls back after the row was written, remove the files. */
    private static void deleteFilesIfRolledBack(PhotoStorage storage, String... keys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(storage, keys);
                }
            }
        });
    }

    /** Best-effort cleanup that never hides the error that caused it. */
    private static void deleteQuietly(PhotoStorage storage, String... keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException e) {
                log.warn("Could not delete orphaned photo file {}", key, e);
            }
        }
    }

    private static ResponseStatusException photoNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
    }
}
