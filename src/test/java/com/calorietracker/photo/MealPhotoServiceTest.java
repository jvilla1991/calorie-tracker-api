package com.calorietracker.photo;

import com.calorietracker.diary.DiaryEntry;
import com.calorietracker.diary.DiaryMeal;
import com.calorietracker.diary.DiaryMealRepository;
import com.calorietracker.photo.MealPhotoService.PhotoContent;
import com.calorietracker.photo.MealPhotoService.Variant;
import com.calorietracker.photo.dto.MealPhotoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Unit tests for {@link MealPhotoService}: mocked repositories, real local storage in a temp dir. */
class MealPhotoServiceTest {

    private static final long MEAL_ID = 7L;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 16);

    @TempDir Path dir;

    private MealPhotoRepository photoRepo;
    private DiaryMealRepository mealRepo;
    private LocalPhotoStorage storage;
    private MealPhotoService service;

    @BeforeEach
    void setUp() {
        photoRepo = mock(MealPhotoRepository.class);
        mealRepo = mock(DiaryMealRepository.class);
        storage = new LocalPhotoStorage(dir);
        service = new MealPhotoService(photoRepo, mealRepo, Optional.of(storage));

        DiaryEntry entry = new DiaryEntry();
        entry.setEntryDate(DAY);
        DiaryMeal meal = new DiaryMeal();
        meal.setDiaryEntry(entry);
        meal.setMealType("dinner");
        ReflectionTestUtils.setField(meal, "id", MEAL_ID);
        when(mealRepo.findByIdForUpdate(MEAL_ID)).thenReturn(Optional.of(meal));
        when(mealRepo.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        when(photoRepo.saveAndFlush(any())).thenAnswer(inv -> {
            MealPhoto p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 41L);
            return p;
        });
    }

    private static MockMultipartFile part(String name, byte[] bytes) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", bytes);
    }

    private MealPhotoResponse upload(byte[] full, byte[] thumb) {
        return service.upload(MEAL_ID, part("full", full), part("thumb", thumb), 1600, 1200);
    }

    private List<Path> storedFiles() throws IOException {
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).toList();
        }
    }

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo((HttpStatusCode) status);
    }

    private MealPhoto stored(long id, String fullKey, String thumbKey) {
        MealPhoto p = new MealPhoto();
        ReflectionTestUtils.setField(p, "id", id);
        p.setDiaryMealId(MEAL_ID);
        p.setFullKey(fullKey);
        p.setThumbKey(thumbKey);
        when(photoRepo.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    // --- upload ----------------------------------------------------------------------------

    @Test
    void upload_storesBothFilesUnderDatedKeys_thenTheRow() throws Exception {
        byte[] full = TestPhotos.jpeg(2000);
        byte[] thumb = TestPhotos.jpeg(300);

        MealPhotoResponse res = upload(full, thumb);

        assertThat(res).isEqualTo(new MealPhotoResponse(41L, "/api/photos/41/thumb", "/api/photos/41/full"));
        ArgumentCaptor<MealPhoto> row = ArgumentCaptor.forClass(MealPhoto.class);
        verify(photoRepo).saveAndFlush(row.capture());
        MealPhoto p = row.getValue();
        assertThat(p.getDiaryMealId()).isEqualTo(MEAL_ID);
        assertThat(p.getFullKey()).matches("meal-photos/2026-10-16/7/[0-9a-f-]{36}-full\\.jpg");
        assertThat(p.getThumbKey()).isEqualTo(p.getFullKey().replace("-full.jpg", "-thumb.jpg"));
        assertThat(p.getWidth()).isEqualTo(1600);
        assertThat(p.getHeight()).isEqualTo(1200);
        assertThat(p.getBytes()).isEqualTo(2000);
        assertThat(Files.readAllBytes(dir.resolve(p.getFullKey()))).isEqualTo(full);
        assertThat(Files.readAllBytes(dir.resolve(p.getThumbKey()))).isEqualTo(thumb);
    }

    @Test
    void upload_withoutDimensions_isAllowed() {
        service.upload(MEAL_ID, part("full", TestPhotos.jpeg(100)), part("thumb", TestPhotos.jpeg(50)), null, null);
        ArgumentCaptor<MealPhoto> row = ArgumentCaptor.forClass(MealPhoto.class);
        verify(photoRepo).saveAndFlush(row.capture());
        assertThat(row.getValue().getWidth()).isNull();
        assertThat(row.getValue().getHeight()).isNull();
    }

    @Test
    void upload_missingOrEmptyPart_is400_andStoresNothing() throws Exception {
        MockMultipartFile ok = part("thumb", TestPhotos.jpeg(50));
        assertStatus(() -> service.upload(MEAL_ID, null, ok, null, null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upload(MEAL_ID, part("full", TestPhotos.jpeg(50)), null, null, null),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upload(MEAL_ID, part("full", new byte[0]), ok, null, null), HttpStatus.BAD_REQUEST);
        assertThat(storedFiles()).isEmpty();
        verifyNoInteractions(photoRepo, mealRepo);
    }

    @Test
    void upload_notAJpeg_is415_andStoresNothing() throws Exception {
        assertStatus(() -> upload(TestPhotos.png(), TestPhotos.jpeg(50)), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(() -> upload(TestPhotos.jpeg(100), TestPhotos.png()), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(() -> upload(new byte[]{(byte) 0xFF, (byte) 0xD8}, TestPhotos.jpeg(50)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(storedFiles()).isEmpty();
        verifyNoInteractions(photoRepo);
    }

    @Test
    void upload_tooLarge_is413_atExactlyTheLimitIsFine() throws Exception {
        int fullMax = (int) MealPhotoService.FULL_MAX_BYTES;   // 3 MB
        int thumbMax = (int) MealPhotoService.THUMB_MAX_BYTES; // 500 KB
        assertStatus(() -> upload(TestPhotos.jpeg(fullMax + 1), TestPhotos.jpeg(50)), HttpStatus.CONTENT_TOO_LARGE);
        assertStatus(() -> upload(TestPhotos.jpeg(100), TestPhotos.jpeg(thumbMax + 1)), HttpStatus.CONTENT_TOO_LARGE);
        assertThat(storedFiles()).isEmpty();

        upload(TestPhotos.jpeg(fullMax), TestPhotos.jpeg(thumbMax));
        assertThat(storedFiles()).hasSize(2);
    }

    @Test
    void upload_dimensionOutOfRange_is400() {
        MockMultipartFile full = part("full", TestPhotos.jpeg(100));
        MockMultipartFile thumb = part("thumb", TestPhotos.jpeg(50));
        assertStatus(() -> service.upload(MEAL_ID, full, thumb, 0, 100), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upload(MEAL_ID, full, thumb, 100, 10_001), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(photoRepo);
    }

    @Test
    void upload_unknownMeal_is404_andStoresNothing() throws Exception {
        assertStatus(() -> service.upload(99L, part("full", TestPhotos.jpeg(100)),
                part("thumb", TestPhotos.jpeg(50)), null, null), HttpStatus.NOT_FOUND);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void upload_seventhPhoto_is409_andStoresNothing() throws Exception {
        when(photoRepo.countByDiaryMealId(MEAL_ID)).thenReturn(5L);
        upload(TestPhotos.jpeg(100), TestPhotos.jpeg(50));   // the 6th is fine
        assertThat(storedFiles()).hasSize(2);

        when(photoRepo.countByDiaryMealId(MEAL_ID)).thenReturn(6L);
        assertStatus(() -> upload(TestPhotos.jpeg(100), TestPhotos.jpeg(50)), HttpStatus.CONFLICT);
        assertThat(storedFiles()).hasSize(2);
        verify(photoRepo, times(1)).saveAndFlush(any());
    }

    @Test
    void upload_usesTheLockingMealLookup() {
        upload(TestPhotos.jpeg(100), TestPhotos.jpeg(50));
        verify(mealRepo).findByIdForUpdate(MEAL_ID);
        verify(mealRepo, never()).findById(anyLong());
    }

    @Test
    void upload_rowInsertFails_deletesBothStoredFiles() throws Exception {
        doThrow(new IllegalStateException("db down")).when(photoRepo).saveAndFlush(any());

        assertThatThrownBy(() -> upload(TestPhotos.jpeg(100), TestPhotos.jpeg(50)))
                .isInstanceOf(IllegalStateException.class).hasMessage("db down");
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void upload_thumbStoreFails_deletesTheFullFile() throws Exception {
        PhotoStorage failing = spy(storage);
        doThrow(new IllegalStateException("disk full"))
                .when(failing).put(argThat(k -> k.endsWith("-thumb.jpg")), any(), any());
        service = new MealPhotoService(photoRepo, mealRepo, Optional.of(failing));

        assertThatThrownBy(() -> upload(TestPhotos.jpeg(100), TestPhotos.jpeg(50)))
                .isInstanceOf(IllegalStateException.class).hasMessage("disk full");
        assertThat(storedFiles()).isEmpty();
        verify(photoRepo, never()).saveAndFlush(any());
    }

    // --- view ------------------------------------------------------------------------------

    @Test
    void load_localStorage_returnsTheJpegBytesOfTheRequestedVariant() {
        storage.put("k/full.jpg", TestPhotos.jpeg(300), "image/jpeg");
        storage.put("k/thumb.jpg", TestPhotos.jpeg(40), "image/jpeg");
        stored(5L, "k/full.jpg", "k/thumb.jpg");

        assertThat(service.load(5L, Variant.FULL))
                .isInstanceOfSatisfying(PhotoContent.Jpeg.class, j -> assertThat(j.bytes()).hasSize(300));
        assertThat(service.load(5L, Variant.THUMB))
                .isInstanceOfSatisfying(PhotoContent.Jpeg.class, j -> assertThat(j.bytes()).hasSize(40));
    }

    @Test
    void load_redirectingStorage_returnsATenMinutePresignedUrl() {
        PhotoStorage s3 = mock(PhotoStorage.class);
        URI url = URI.create("https://bucket.s3.amazonaws.com/k/thumb.jpg?X-Amz-Signature=x");
        when(s3.presignedGetUrl("k/thumb.jpg", Duration.ofMinutes(10))).thenReturn(Optional.of(url));
        service = new MealPhotoService(photoRepo, mealRepo, Optional.of(s3));
        stored(5L, "k/full.jpg", "k/thumb.jpg");

        assertThat(service.load(5L, Variant.THUMB)).isEqualTo(new PhotoContent.Redirect(url));
        verify(s3, never()).read(any());
    }

    @Test
    void load_unknownPhotoOrMissingFile_is404() {
        when(photoRepo.findById(5L)).thenReturn(Optional.empty());
        assertStatus(() -> service.load(5L, Variant.THUMB), HttpStatus.NOT_FOUND);

        stored(6L, "gone/full.jpg", "gone/thumb.jpg");
        assertStatus(() -> service.load(6L, Variant.FULL), HttpStatus.NOT_FOUND);
    }

    // --- delete ----------------------------------------------------------------------------

    @Test
    void delete_removesBothFilesThenTheRow() throws Exception {
        storage.put("k/full.jpg", TestPhotos.jpeg(300), "image/jpeg");
        storage.put("k/thumb.jpg", TestPhotos.jpeg(40), "image/jpeg");
        MealPhoto p = stored(5L, "k/full.jpg", "k/thumb.jpg");

        service.delete(5L);

        assertThat(storedFiles()).isEmpty();
        verify(photoRepo).delete(p);
    }

    @Test
    void delete_filesAlreadyGone_stillDeletesTheRow() {
        MealPhoto p = stored(5L, "gone/full.jpg", "gone/thumb.jpg");
        service.delete(5L);
        verify(photoRepo).delete(p);
    }

    @Test
    void delete_unknownPhoto_is404() {
        when(photoRepo.findById(5L)).thenReturn(Optional.empty());
        assertStatus(() -> service.delete(5L), HttpStatus.NOT_FOUND);
        verify(photoRepo, never()).delete(any());
    }

    // --- storage not configured (prod without MEDIA_BUCKET) --------------------------------

    @Test
    void noStorage_uploadViewDeleteAre503_butTheDiaryListStillWorks() {
        service = new MealPhotoService(photoRepo, mealRepo, Optional.empty());

        assertStatus(() -> upload(TestPhotos.jpeg(100), TestPhotos.jpeg(50)), HttpStatus.SERVICE_UNAVAILABLE);
        assertStatus(() -> service.load(5L, Variant.THUMB), HttpStatus.SERVICE_UNAVAILABLE);
        assertStatus(() -> service.delete(5L), HttpStatus.SERVICE_UNAVAILABLE);
        assertThatThrownBy(() -> service.delete(5L)).hasMessageContaining(MealPhotoService.NOT_CONFIGURED);

        when(photoRepo.findAllForDate(DAY)).thenReturn(List.of());
        assertThat(service.byMealForDay(DAY)).isEmpty();
    }

    // --- listing ---------------------------------------------------------------------------

    @Test
    void byMealForDay_groupsTheDaysPhotosByMeal_keepingIdOrder() {
        MealPhoto a = new MealPhoto();
        ReflectionTestUtils.setField(a, "id", 1L);
        a.setDiaryMealId(7L);
        MealPhoto b = new MealPhoto();
        ReflectionTestUtils.setField(b, "id", 2L);
        b.setDiaryMealId(8L);
        MealPhoto c = new MealPhoto();
        ReflectionTestUtils.setField(c, "id", 3L);
        c.setDiaryMealId(7L);
        when(photoRepo.findAllForDate(DAY)).thenReturn(List.of(a, b, c));

        Map<Long, List<MealPhotoResponse>> byMeal = service.byMealForDay(DAY);

        assertThat(byMeal).containsOnlyKeys(7L, 8L);
        assertThat(byMeal.get(7L)).extracting(MealPhotoResponse::id).containsExactly(1L, 3L);
        assertThat(byMeal.get(8L)).containsExactly(MealPhotoResponse.of(2L));
        verify(photoRepo, times(1)).findAllForDate(DAY);
        verifyNoMoreInteractions(photoRepo);
    }

    @Test
    void isJpeg_checksTheThreeLeadingBytes() {
        assertThat(MealPhotoService.isJpeg(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})).isTrue();
        assertThat(MealPhotoService.isJpeg(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0x00})).isFalse();
        assertThat(MealPhotoService.isJpeg(new byte[]{(byte) 0xFF, (byte) 0xD8})).isFalse();
        assertThat(MealPhotoService.isJpeg(TestPhotos.png())).isFalse();
    }
}
