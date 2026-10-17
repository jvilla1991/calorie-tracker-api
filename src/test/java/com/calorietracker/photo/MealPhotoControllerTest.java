package com.calorietracker.photo;

import com.calorietracker.diary.DiaryEntry;
import com.calorietracker.diary.DiaryMeal;
import com.calorietracker.diary.DiaryMealRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP contract of the photo endpoints: standalone MockMvc with the real {@link MealPhotoService}
 * over mocked repositories and local storage in a temp dir. Container limits (multipart caps) are
 * not applied by MockMvc; they are covered by {@code PhotoUploadLimitsIT} on a real server.
 */
class MealPhotoControllerTest {

    private static final long MEAL_ID = 7L;

    @TempDir Path dir;

    private MealPhotoRepository photoRepo;
    private DiaryMealRepository mealRepo;
    private LocalPhotoStorage storage;

    @BeforeEach
    void setUp() {
        photoRepo = mock(MealPhotoRepository.class);
        mealRepo = mock(DiaryMealRepository.class);
        storage = new LocalPhotoStorage(dir);

        DiaryEntry entry = new DiaryEntry();
        entry.setEntryDate(LocalDate.of(2026, 10, 16));
        DiaryMeal meal = new DiaryMeal();
        meal.setDiaryEntry(entry);
        meal.setMealType("lunch");
        ReflectionTestUtils.setField(meal, "id", MEAL_ID);
        when(mealRepo.findByIdForUpdate(MEAL_ID)).thenReturn(Optional.of(meal));
        when(mealRepo.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        when(photoRepo.saveAndFlush(any())).thenAnswer(inv -> {
            MealPhoto p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 41L);
            when(photoRepo.findById(41L)).thenReturn(Optional.of(p));
            return p;
        });
    }

    private MockMvc mvc(Optional<PhotoStorage> photoStorage) {
        return MockMvcBuilders
                .standaloneSetup(new MealPhotoController(new MealPhotoService(photoRepo, mealRepo, photoStorage)))
                .setControllerAdvice(new MultipartErrorAdvice())
                .build();
    }

    private MockMvc mvc() {
        return mvc(Optional.of(storage));
    }

    private ResultActions upload(MockMvc mvc, long mealId, byte[] full, byte[] thumb) throws Exception {
        var req = multipart("/api/diary/meals/" + mealId + "/photos");
        if (full != null) req.file(new MockMultipartFile("full", "full.jpg", "image/jpeg", full));
        if (thumb != null) req.file(new MockMultipartFile("thumb", "thumb.jpg", "image/jpeg", thumb));
        return mvc.perform(req.param("width", "1600").param("height", "1200"));
    }

    private MealPhoto storedPhoto(long id) {
        storage.put("k/" + id + "-full.jpg", TestPhotos.jpeg(300), "image/jpeg");
        storage.put("k/" + id + "-thumb.jpg", TestPhotos.jpeg(40), "image/jpeg");
        MealPhoto p = new MealPhoto();
        ReflectionTestUtils.setField(p, "id", id);
        p.setDiaryMealId(MEAL_ID);
        p.setFullKey("k/" + id + "-full.jpg");
        p.setThumbKey("k/" + id + "-thumb.jpg");
        when(photoRepo.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    // --- upload ----------------------------------------------------------------------------

    @Test
    void upload_is201WithIdAndApiUrls() throws Exception {
        upload(mvc(), MEAL_ID, TestPhotos.jpeg(2000), TestPhotos.jpeg(300))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.thumbUrl").value("/api/photos/41/thumb"))
                .andExpect(jsonPath("$.fullUrl").value("/api/photos/41/full"));
    }

    @Test
    void upload_missingParts_is400WithMessage() throws Exception {
        upload(mvc(), MEAL_ID, TestPhotos.jpeg(100), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Both 'full' and 'thumb' JPEG parts are required"));
        // Not multipart at all.
        mvc().perform(post("/api/diary/meals/7/photos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        verify(photoRepo, never()).saveAndFlush(any());
    }

    @Test
    void upload_notAJpeg_is415WithMessage() throws Exception {
        upload(mvc(), MEAL_ID, TestPhotos.png(), TestPhotos.jpeg(100))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("'full' must be a JPEG image"));
    }

    @Test
    void upload_overTheAppLimits_is413WithMessage() throws Exception {
        upload(mvc(), MEAL_ID, TestPhotos.jpeg((int) MealPhotoService.FULL_MAX_BYTES + 1), TestPhotos.jpeg(100))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.message").value("'full' must be at most 3 MB"));
        upload(mvc(), MEAL_ID, TestPhotos.jpeg(100), TestPhotos.jpeg((int) MealPhotoService.THUMB_MAX_BYTES + 1))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.message").value("'thumb' must be at most 500 KB"));
    }

    @Test
    void upload_unknownMeal_is404WithMessage() throws Exception {
        upload(mvc(), 99L, TestPhotos.jpeg(100), TestPhotos.jpeg(50))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Meal not found"));
    }

    @Test
    void upload_whenTheMealHasSixPhotos_is409WithMessage() throws Exception {
        when(photoRepo.countByDiaryMealId(MEAL_ID)).thenReturn(6L);
        upload(mvc(), MEAL_ID, TestPhotos.jpeg(100), TestPhotos.jpeg(50))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("A meal can have at most 6 photos"));
    }

    @Test
    void upload_badDimension_is400() throws Exception {
        mvc().perform(multipart("/api/diary/meals/7/photos")
                        .file(new MockMultipartFile("full", "f.jpg", "image/jpeg", TestPhotos.jpeg(100)))
                        .file(new MockMultipartFile("thumb", "t.jpg", "image/jpeg", TestPhotos.jpeg(50)))
                        .param("width", "-1"))
                .andExpect(status().isBadRequest());
        mvc().perform(multipart("/api/diary/meals/7/photos")
                        .file(new MockMultipartFile("full", "f.jpg", "image/jpeg", TestPhotos.jpeg(100)))
                        .file(new MockMultipartFile("thumb", "t.jpg", "image/jpeg", TestPhotos.jpeg(50)))
                        .param("width", "wide"))
                .andExpect(status().isBadRequest());
        verify(photoRepo, never()).saveAndFlush(any());
    }

    // --- view ------------------------------------------------------------------------------

    @Test
    void get_localStorage_is200JpegBytes_withPrivateShortCaching() throws Exception {
        storedPhoto(5L);
        mvc().perform(get("/api/photos/5/thumb"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "private, max-age=300"))
                .andExpect(content().bytes(TestPhotos.jpeg(40)));
        mvc().perform(get("/api/photos/5/full"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(TestPhotos.jpeg(300)));
    }

    @Test
    void get_redirectingStorage_is302ToThePresignedUrl_withPrivateShortCaching() throws Exception {
        storedPhoto(5L);
        PhotoStorage s3 = mock(PhotoStorage.class);
        when(s3.presignedGetUrl("k/5-full.jpg", Duration.ofMinutes(10)))
                .thenReturn(Optional.of(URI.create("https://bucket.s3.amazonaws.com/k/5-full.jpg?X-Amz-Signature=abc%2Fdef")));

        mvc(Optional.of(s3)).perform(get("/api/photos/5/full"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://bucket.s3.amazonaws.com/k/5-full.jpg?X-Amz-Signature=abc%2Fdef"))
                .andExpect(header().string("Cache-Control", "private, max-age=300"));
    }

    @Test
    void get_unknownPhoto_is404WithMessage() throws Exception {
        when(photoRepo.findById(5L)).thenReturn(Optional.empty());
        mvc().perform(get("/api/photos/5/thumb"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Photo not found"));
    }

    // --- delete ----------------------------------------------------------------------------

    @Test
    void delete_is204_andRemovesTheFiles() throws Exception {
        MealPhoto p = storedPhoto(5L);
        mvc().perform(delete("/api/photos/5")).andExpect(status().isNoContent());
        assertThat(Files.exists(dir.resolve("k/5-full.jpg"))).isFalse();
        assertThat(Files.exists(dir.resolve("k/5-thumb.jpg"))).isFalse();
        verify(photoRepo).delete(p);
    }

    @Test
    void delete_unknownPhoto_is404() throws Exception {
        when(photoRepo.findById(5L)).thenReturn(Optional.empty());
        mvc().perform(delete("/api/photos/5")).andExpect(status().isNotFound());
    }

    @Test
    void uploadedPhoto_canBeViewedThenDeleted() throws Exception {
        MockMvc mvc = mvc();
        upload(mvc, MEAL_ID, TestPhotos.jpeg(2000), TestPhotos.jpeg(300)).andExpect(status().isCreated());
        mvc.perform(get("/api/photos/41/thumb")).andExpect(status().isOk())
                .andExpect(content().bytes(TestPhotos.jpeg(300)));
        mvc.perform(delete("/api/photos/41")).andExpect(status().isNoContent());
        try (var files = Files.walk(dir)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    // --- storage not configured ------------------------------------------------------------

    @Test
    void noStorage_everyPhotoEndpointIs503WithMessage() throws Exception {
        MockMvc mvc = mvc(Optional.empty());
        upload(mvc, MEAL_ID, TestPhotos.jpeg(100), TestPhotos.jpeg(50))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Photo storage not configured"));
        mvc.perform(get("/api/photos/5/thumb"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Photo storage not configured"));
        mvc.perform(delete("/api/photos/5"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Photo storage not configured"));
    }
}
