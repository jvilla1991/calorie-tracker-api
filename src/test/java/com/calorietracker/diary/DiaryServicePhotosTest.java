package com.calorietracker.diary;

import com.calorietracker.diary.dto.DiaryMealResponse;
import com.calorietracker.diary.dto.DiaryResponse;
import com.calorietracker.diary.dto.MealDetailsRequest;
import com.calorietracker.eatingwindow.EatingWindowService;
import com.calorietracker.exercise.ExerciseService;
import com.calorietracker.food.FoodService;
import com.calorietracker.photo.MealPhotoService;
import com.calorietracker.photo.dto.MealPhotoResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** How {@link DiaryService} attaches meal photos (Mockito, no DB). */
@ExtendWith(MockitoExtension.class)
class DiaryServicePhotosTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 16);

    @Mock DiaryEntryRepository entryRepo;
    @Mock DiaryMealRepository mealRepo;
    @Mock DiaryItemRepository itemRepo;
    @Mock FoodService foodService;
    @Mock ExerciseService exerciseService;
    @Mock EatingWindowService eatingWindowService;
    @Mock MealPhotoService photoService;

    @InjectMocks DiaryService diaryService;

    private static DiaryMeal meal(DiaryEntry entry, long id, String type, int order) {
        DiaryMeal m = new DiaryMeal();
        ReflectionTestUtils.setField(m, "id", id);
        m.setDiaryEntry(entry);
        m.setMealType(type);
        m.setDisplayOrder(order);
        entry.getMeals().add(m);
        return m;
    }

    @Test
    void getDay_attachesEachMealsPhotos_fromOneQueryForTheWholeDay() {
        DiaryEntry entry = new DiaryEntry();
        entry.setEntryDate(DAY);
        meal(entry, 1L, "breakfast", 0);
        meal(entry, 2L, "lunch", 1);
        meal(entry, 3L, "dinner", 2);
        when(entryRepo.findByEntryDate(DAY)).thenReturn(Optional.of(entry));
        when(photoService.byMealForDay(DAY)).thenReturn(Map.of(
                1L, List.of(MealPhotoResponse.of(10L), MealPhotoResponse.of(11L)),
                3L, List.of(MealPhotoResponse.of(12L))));

        DiaryResponse day = diaryService.getDay(DAY);

        assertThat(day.meals()).extracting(DiaryMealResponse::id).containsExactly(1L, 2L, 3L);
        assertThat(day.meals().get(0).photos()).extracting(MealPhotoResponse::id).containsExactly(10L, 11L);
        assertThat(day.meals().get(1).photos()).isEmpty();
        assertThat(day.meals().get(2).photos()).containsExactly(
                new MealPhotoResponse(12L, "/api/photos/12/thumb", "/api/photos/12/full"));
        verify(photoService, times(1)).byMealForDay(DAY);
        verifyNoMoreInteractions(photoService);
    }

    @Test
    void getDay_withoutMeals_skipsThePhotoQuery() {
        when(entryRepo.findByEntryDate(DAY)).thenReturn(Optional.empty());

        assertThat(diaryService.getDay(DAY).meals()).isEmpty();
        verifyNoInteractions(photoService);
    }

    @Test
    void updateDetails_returnsTheMealWithItsPhotos() {
        DiaryEntry entry = new DiaryEntry();
        entry.setEntryDate(DAY);
        DiaryMeal m = meal(entry, 4L, "dinner", 0);
        when(mealRepo.findById(4L)).thenReturn(Optional.of(m));
        when(mealRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(photoService.forMeal(4L)).thenReturn(List.of(MealPhotoResponse.of(20L)));

        DiaryMealResponse res = diaryService.updateDetails(4L, new MealDetailsRequest("Out", new BigDecimal("700")));

        assertThat(res.photos()).containsExactly(MealPhotoResponse.of(20L));
        assertThat(res.estimatedCalories()).isEqualTo(700);
    }
}
