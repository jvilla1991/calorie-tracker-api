package com.calorietracker.photo;

import com.calorietracker.diary.DiaryService;
import com.calorietracker.diary.dto.AddMealRequest;
import com.calorietracker.diary.dto.DiaryMealResponse;
import com.calorietracker.diary.dto.DiaryResponse;
import com.calorietracker.photo.dto.MealPhotoResponse;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Meal photos against real Postgres (V12 migration, FK cascade, per-meal limit, diary wiring) and
 * the application's real MVC stack, with the context's own storage (local files, local profile).
 * {@code @Transactional} rolls every row back, and the service deletes the files it stored when
 * the transaction rolls back; {@link #storedFilesAreGoneAfterRollback} checks that.
 */
@SpringBootTest
@Transactional
class MealPhotoIT {

    // Far in the past so it cannot collide with real diary dates.
    private static final LocalDate D1 = LocalDate.of(1990, 12, 1);

    @Autowired MealPhotoService photoService;
    @Autowired MealPhotoRepository photoRepo;
    @Autowired DiaryService diaryService;
    @Autowired PhotoStorage storage;
    @Autowired EntityManager em;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;
    private final List<String> storedKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterTransaction
    void storedFilesAreGoneAfterRollback() {
        for (String key : storedKeys) {
            assertThat(storage.read(key)).as("file %s after rollback", key).isEmpty();
        }
    }

    private Long addMeal(String mealType) {
        AddMealRequest req = new AddMealRequest();
        req.setMealType(mealType);
        return diaryService.addMeal(D1, req).id();
    }

    private MealPhotoResponse upload(Long mealId) {
        MealPhotoResponse res = photoService.upload(mealId,
                new MockMultipartFile("full", "full.jpg", "image/jpeg", TestPhotos.jpeg(2000)),
                new MockMultipartFile("thumb", "thumb.jpg", "image/jpeg", TestPhotos.jpeg(200)),
                1600, 1200);
        MealPhoto row = photoRepo.findById(res.id()).orElseThrow();
        storedKeys.add(row.getFullKey());
        storedKeys.add(row.getThumbKey());
        return res;
    }

    private DiaryMealResponse meal(DiaryResponse day, Long mealId) {
        return day.meals().stream().filter(m -> m.id().equals(mealId)).findFirst().orElseThrow();
    }

    @Test
    void upload_storesFilesAndRow_andTheDiaryListsEachMealsPhotosInOrder() {
        Long breakfast = addMeal("breakfast");
        Long dinner = addMeal("dinner");
        MealPhotoResponse b1 = upload(breakfast);
        MealPhotoResponse d1 = upload(dinner);
        MealPhotoResponse b2 = upload(breakfast);

        MealPhoto row = photoRepo.findById(b1.id()).orElseThrow();
        assertThat(row.getDiaryMealId()).isEqualTo(breakfast);
        assertThat(row.getFullKey()).startsWith("meal-photos/1990-12-01/" + breakfast + "/").endsWith("-full.jpg");
        assertThat(row.getWidth()).isEqualTo(1600);
        assertThat(row.getBytes()).isEqualTo(2000);
        assertThat(row.getCreatedAt()).isNotNull();
        assertThat(storage.read(row.getFullKey())).hasValueSatisfying(b -> assertThat(b).hasSize(2000));
        assertThat(storage.read(row.getThumbKey())).hasValueSatisfying(b -> assertThat(b).hasSize(200));

        em.flush();
        em.clear();
        DiaryResponse day = diaryService.getDay(D1);
        assertThat(meal(day, breakfast).photos()).containsExactly(b1, b2);
        assertThat(meal(day, dinner).photos()).containsExactly(d1);
        assertThat(b1.thumbUrl()).isEqualTo("/api/photos/" + b1.id() + "/thumb");
    }

    @Test
    void sixPhotosPerMeal_theSeventhIs409() {
        Long lunch = addMeal("lunch");
        for (int i = 0; i < MealPhotoService.MAX_PHOTOS_PER_MEAL; i++) {
            upload(lunch);
        }
        assertThatThrownBy(() -> upload(lunch))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(photoRepo.countByDiaryMealId(lunch)).isEqualTo(6);
        // Another meal is unaffected.
        upload(addMeal("dinner"));
    }

    @Test
    void deletingAMeal_cascadesToItsPhotoRows() {
        Long snack = addMeal("snack");
        MealPhotoResponse photo = upload(snack);
        em.flush();

        em.createNativeQuery("DELETE FROM diary_meals WHERE id = :id").setParameter("id", snack).executeUpdate();
        em.clear();

        assertThat(photoRepo.findById(photo.id())).isEmpty();
    }

    @Test
    void dbRejectsAPhotoForAMissingMeal() {
        MealPhoto orphan = new MealPhoto();
        orphan.setDiaryMealId(999_999_999L);
        orphan.setFullKey("x-full.jpg");
        orphan.setThumbKey("x-thumb.jpg");
        assertThatThrownBy(() -> photoRepo.saveAndFlush(orphan)).isInstanceOf(Exception.class);
    }

    @Test
    void delete_removesTheFilesAndTheRow() {
        MealPhotoResponse photo = upload(addMeal("lunch"));
        MealPhoto row = photoRepo.findById(photo.id()).orElseThrow();

        photoService.delete(photo.id());
        photoRepo.flush();

        assertThat(photoRepo.findById(photo.id())).isEmpty();
        assertThat(storage.read(row.getFullKey())).isEmpty();
        assertThat(storage.read(row.getThumbKey())).isEmpty();
    }

    // --- JSON and bytes over the real MVC stack (security on, logged in) --------------------

    @Test
    void json_uploadViewListDelete() throws Exception {
        Long dinner = addMeal("dinner");

        String body = mvc.perform(multipart("/api/diary/meals/" + dinner + "/photos").with(user("admin"))
                        .file(new MockMultipartFile("full", "full.jpg", "image/jpeg", TestPhotos.jpeg(3000)))
                        .file(new MockMultipartFile("thumb", "thumb.jpg", "image/jpeg", TestPhotos.jpeg(300)))
                        .param("width", "1600").param("height", "900"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.thumbUrl").exists())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        MealPhoto row = photoRepo.findById(id).orElseThrow();
        storedKeys.add(row.getFullKey());
        storedKeys.add(row.getThumbKey());

        mvc.perform(get("/api/photos/" + id + "/thumb").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "private, max-age=300"))
                .andExpect(content().bytes(TestPhotos.jpeg(300)));
        em.flush();
        em.clear();

        mvc.perform(get("/api/diary/1990-12-01").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meals[0].photos.length()").value(1))
                .andExpect(jsonPath("$.meals[0].photos[0].id").value(id))
                .andExpect(jsonPath("$.meals[0].photos[0].thumbUrl").value("/api/photos/" + id + "/thumb"))
                .andExpect(jsonPath("$.meals[0].photos[0].fullUrl").value("/api/photos/" + id + "/full"));

        mvc.perform(delete("/api/photos/" + id).with(user("admin"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/photos/" + id + "/full").with(user("admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Photo not found"));
    }

    @Test
    void json_mealWithoutPhotos_hasAnEmptyPhotosArray() throws Exception {
        addMeal("snack");
        em.flush();
        em.clear();

        mvc.perform(get("/api/diary/1990-12-01").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meals[0].photos").isArray())
                .andExpect(jsonPath("$.meals[0].photos.length()").value(0));
    }
}
