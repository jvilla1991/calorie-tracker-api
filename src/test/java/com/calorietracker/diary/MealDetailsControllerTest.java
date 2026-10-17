package com.calorietracker.diary;

import com.calorietracker.eatingwindow.EatingWindowService;
import com.calorietracker.exercise.ExerciseService;
import com.calorietracker.food.Food;
import com.calorietracker.food.FoodService;
import com.calorietracker.photo.MealPhotoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of {@code PUT /api/diary/meals/{mealId}/details}: standalone MockMvc with the real
 * {@link DiaryService} over mocked repositories, so the service-side note rules run over HTTP too.
 */
class MealDetailsControllerTest {

    private DiaryMealRepository mealRepo;
    private DiaryMeal meal;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        DiaryEntry entry = new DiaryEntry();
        entry.setEntryDate(LocalDate.of(2026, 10, 1));
        meal = new DiaryMeal();
        meal.setDiaryEntry(entry);
        meal.setMealType("dinner");
        ReflectionTestUtils.setField(meal, "id", 7L);
        // One logged item: 200 kcal and 10 g protein per 100 g, 150 g logged -> 300 kcal, 15 g protein.
        Food food = new Food();
        food.setName("Rice bowl");
        food.setCaloriesPer100g(new BigDecimal("200"));
        food.setProteinPer100g(new BigDecimal("10"));
        food.setCarbsPer100g(new BigDecimal("30"));
        food.setFatPer100g(new BigDecimal("5"));
        DiaryItem item = new DiaryItem();
        item.setDiaryMeal(meal);
        item.setFood(food);
        item.setQuantityGrams(new BigDecimal("150"));
        meal.getItems().add(item);

        mealRepo = mock(DiaryMealRepository.class);
        when(mealRepo.findById(7L)).thenReturn(Optional.of(meal));
        when(mealRepo.findById(99L)).thenReturn(Optional.empty());
        when(mealRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DiaryService service = new DiaryService(mock(DiaryEntryRepository.class), mealRepo,
                mock(DiaryItemRepository.class), mock(FoodService.class),
                mock(ExerciseService.class), mock(EatingWindowService.class),
                mock(MealPhotoService.class));
        mvc = MockMvcBuilders
                .standaloneSetup(new DiaryController(service, mock(DiarySummaryService.class)))
                .build();
    }

    private ResultActions putDetails(long mealId, String json) throws Exception {
        return mvc.perform(put("/api/diary/meals/" + mealId + "/details")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String noteJson, String estimate) {
        return "{\"note\":" + noteJson + ",\"estimatedCalories\":" + estimate + "}";
    }

    @Test
    void put_is200WithTheMeal_trimsNote_andCountsTheEstimateInCaloriesOnly() throws Exception {
        putDetails(7, body("\"  Pad thai at Lotus  \"", "900"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.mealType").value("dinner"))
                .andExpect(jsonPath("$.note").value("Pad thai at Lotus"))
                .andExpect(jsonPath("$.estimatedCalories").value(900))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totals.calories").value(1200))  // 300 logged + 900 estimated
                .andExpect(jsonPath("$.totals.protein").value(15));    // the estimate adds no macros

        assertThat(meal.getNote()).isEqualTo("Pad thai at Lotus");
        assertThat(meal.getEstimatedCalories()).isEqualTo(900);
        verify(mealRepo).save(meal);
    }

    @Test
    void put_nullsClearBothFields_andMissingFieldsCountAsNull() throws Exception {
        meal.setNote("old");
        meal.setEstimatedCalories(500);

        putDetails(7, body("null", "null"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value(nullValue()))
                .andExpect(jsonPath("$.estimatedCalories").value(nullValue()))
                .andExpect(jsonPath("$.totals.calories").value(300));
        assertThat(meal.getNote()).isNull();
        assertThat(meal.getEstimatedCalories()).isNull();

        meal.setNote("old");
        meal.setEstimatedCalories(500);
        putDetails(7, "{}").andExpect(status().isOk());
        assertThat(meal.getNote()).isNull();
        assertThat(meal.getEstimatedCalories()).isNull();
    }

    @Test
    void put_replacesPreviousValues() throws Exception {
        putDetails(7, body("\"First\"", "400")).andExpect(status().isOk());
        putDetails(7, body("\"Second\"", "650"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("Second"))
                .andExpect(jsonPath("$.estimatedCalories").value(650))
                .andExpect(jsonPath("$.totals.calories").value(950));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"   \"", "\"\\n\\t \""})
    void put_blankNote_isStoredAsNull(String noteJson) throws Exception {
        putDetails(7, body(noteJson, "300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value(nullValue()));
        assertThat(meal.getNote()).isNull();
    }

    @Test
    void put_noteLengthLimitAppliesAfterTrimming() throws Exception {
        putDetails(7, body("\"" + "a".repeat(500) + "\"", "null")).andExpect(status().isOk());
        putDetails(7, body("\"   " + "a".repeat(500) + "   \"", "null")).andExpect(status().isOk());

        clearInvocations(mealRepo);
        putDetails(7, body("\"" + "a".repeat(501) + "\"", "null")).andExpect(status().isBadRequest());
        verify(mealRepo, never()).save(any());
    }

    @Test
    void put_estimateBoundaries_areAccepted() throws Exception {
        putDetails(7, body("null", "1")).andExpect(status().isOk());
        putDetails(7, body("null", "10000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedCalories").value(10000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "10001", "900.5", "\"lots\""})
    void put_badEstimate_is400(String estimate) throws Exception {
        putDetails(7, body("\"Dinner out\"", estimate)).andExpect(status().isBadRequest());
        verify(mealRepo, never()).save(any());
    }

    @Test
    void put_unknownMeal_is404() throws Exception {
        putDetails(99, body("\"Dinner out\"", "900")).andExpect(status().isNotFound());
        verify(mealRepo, never()).save(any());
    }

    @Test
    void put_malformedBodyOrMealId_is400() throws Exception {
        putDetails(7, "not json").andExpect(status().isBadRequest());
        mvc.perform(put("/api/diary/meals/abc/details")
                        .contentType(MediaType.APPLICATION_JSON).content(body("null", "100")))
                .andExpect(status().isBadRequest());
        verify(mealRepo, never()).save(any());
    }
}
