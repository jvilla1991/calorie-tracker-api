package com.calorietracker.diary;

import com.calorietracker.diary.dto.AddItemRequest;
import com.calorietracker.diary.dto.AddMealRequest;
import com.calorietracker.diary.dto.DailySummaryDto;
import com.calorietracker.diary.dto.DiaryMealResponse;
import com.calorietracker.diary.dto.DiaryResponse;
import com.calorietracker.diary.dto.MealDetailsRequest;
import com.calorietracker.food.Food;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Meal note and calorie estimate against real Postgres (V11 migration, diary and summary totals)
 * and the application's real MVC/Jackson stack. {@code @Transactional} rolls every row back.
 */
@SpringBootTest
@Transactional
class MealDetailsIT {

    // Far in the past so it cannot collide with real diary dates.
    private static final LocalDate D1 = LocalDate.of(1990, 11, 1);

    @Autowired DiaryService diaryService;
    @Autowired DiarySummaryService summaryService;
    @Autowired DiaryMealRepository mealRepo;
    @Autowired EntityManager em;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private Long addMeal(LocalDate date, String mealType) {
        AddMealRequest req = new AddMealRequest();
        req.setMealType(mealType);
        return diaryService.addMeal(date, req).id();
    }

    /** 200 kcal and 10 g protein per 100 g. */
    private Food food() {
        Food f = new Food();
        f.setName("Rice bowl");
        f.setSource("custom");
        f.setCaloriesPer100g(new BigDecimal("200"));
        f.setProteinPer100g(new BigDecimal("10"));
        f.setCarbsPer100g(new BigDecimal("30"));
        f.setFatPer100g(new BigDecimal("5"));
        em.persist(f);
        return f;
    }

    private void addItem(Long mealId, Food f, String grams) {
        AddItemRequest req = new AddItemRequest();
        req.setFoodId(f.getId());
        req.setQuantityGrams(new BigDecimal(grams));
        diaryService.addItem(mealId, req);
    }

    private void details(Long mealId, String note, Integer estimate) {
        diaryService.updateDetails(mealId,
                new MealDetailsRequest(note, estimate == null ? null : BigDecimal.valueOf(estimate)));
    }

    private DiaryMealResponse mealOnDay(LocalDate date, Long mealId) {
        em.flush();
        em.clear();
        return diaryService.getDay(date).meals().stream()
                .filter(m -> m.id().equals(mealId)).findFirst().orElseThrow();
    }

    @Test
    void set_replace_clear() {
        Long id = addMeal(D1, "dinner");

        details(id, "  Pad thai at Lotus ", 900);
        DiaryMealResponse meal = mealOnDay(D1, id);
        assertThat(meal.note()).isEqualTo("Pad thai at Lotus");
        assertThat(meal.estimatedCalories()).isEqualTo(900);
        assertThat(meal.totals().getCalories()).isEqualByComparingTo("900");

        details(id, "Burger", 1100);
        meal = mealOnDay(D1, id);
        assertThat(meal.note()).isEqualTo("Burger");
        assertThat(meal.estimatedCalories()).isEqualTo(1100);

        details(id, "   ", null);
        meal = mealOnDay(D1, id);
        assertThat(meal.note()).isNull();
        assertThat(meal.estimatedCalories()).isNull();
        assertThat(meal.totals().getCalories()).isEqualByComparingTo("0");
    }

    @Test
    void unknownMeal_is404() {
        assertThatThrownBy(() -> details(999_999_999L, "x", 100))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_001})
    void dbRejectsEstimatesOutsideOneToTenThousand(int calories) {
        Long id = addMeal(D1, "lunch");
        DiaryMeal meal = mealRepo.findById(id).orElseThrow();
        meal.setEstimatedCalories(calories);
        assertThatThrownBy(() -> mealRepo.saveAndFlush(meal)).isInstanceOf(Exception.class);
    }

    @Test
    void mealAndDayTotalsIncludeEstimates_caloriesOnly_andTheSummaryAgrees() {
        Food rice = food();
        Long breakfast = addMeal(D1, "breakfast");
        addItem(breakfast, rice, "150");             // 300 kcal, 15 g protein
        details(breakfast, "Plus a latte", 120);
        Long dinner = addMeal(D1, "dinner");
        details(dinner, "Dinner out", 900);         // nothing logged
        Long otherDay = addMeal(D1.plusDays(1), "lunch");
        details(otherDay, null, 450);
        em.flush();
        em.clear();

        DiaryResponse day = diaryService.getDay(D1);
        DiaryMealResponse b = day.meals().stream().filter(m -> m.id().equals(breakfast)).findFirst().orElseThrow();
        DiaryMealResponse d = day.meals().stream().filter(m -> m.id().equals(dinner)).findFirst().orElseThrow();
        assertThat(b.totals().getCalories()).isEqualByComparingTo("420");
        assertThat(b.totals().getProtein()).isEqualByComparingTo("15");
        assertThat(d.totals().getCalories()).isEqualByComparingTo("900");
        assertThat(d.totals().getProtein()).isEqualByComparingTo("0");
        assertThat(day.totals().getCalories()).isEqualByComparingTo("1320");
        assertThat(day.totals().getProtein()).isEqualByComparingTo("15");

        var rows = summaryService.summarize(D1, D1.plusDays(2));
        assertThat(rows).extracting(DailySummaryDto::date).containsExactly(D1, D1.plusDays(1), D1.plusDays(2));
        assertThat(rows.get(0).calories()).isEqualTo(day.totals().getCalories());
        assertThat(rows.get(0).protein()).isEqualTo(day.totals().getProtein());
        assertThat(rows.get(0).carbs()).isEqualTo(day.totals().getCarbs());
        assertThat(rows.get(0).fat()).isEqualTo(day.totals().getFat());
        assertThat(rows.get(0).fiber()).isEqualTo(day.totals().getFiber());
        assertThat(rows.get(1).calories()).isEqualByComparingTo("450");   // an estimate-only day
        assertThat(rows.get(1).calories()).isEqualTo(diaryService.getDay(D1.plusDays(1)).totals().getCalories());
        assertThat(rows.get(2).calories()).isEqualByComparingTo("0");
    }

    // --- JSON over the real MVC stack (application Jackson config, bean validation) -------

    @Test
    void json_putReturnsTheMeal_andTheDiaryShowsTheDetails() throws Exception {
        Long id = addMeal(D1, "dinner");

        mvc.perform(put("/api/diary/meals/" + id + "/details").with(user("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Pad thai at Lotus\",\"estimatedCalories\":900}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.note").value("Pad thai at Lotus"))
                .andExpect(jsonPath("$.estimatedCalories").value(900))
                .andExpect(jsonPath("$.totals.calories").value(900));
        em.flush();
        em.clear();

        mvc.perform(get("/api/diary/1990-11-01").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meals[0].note").value("Pad thai at Lotus"))
                .andExpect(jsonPath("$.meals[0].estimatedCalories").value(900))
                .andExpect(jsonPath("$.meals[0].totals.calories").value(900))
                .andExpect(jsonPath("$.totals.calories").value(900));

        mvc.perform(get("/api/diary/summary").param("from", "1990-11-01").param("to", "1990-11-01")
                        .with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].calories").value(900));
    }

    @Test
    void json_mealWithoutDetails_hasNullFields() throws Exception {
        addMeal(D1, "snack");
        em.flush();
        em.clear();

        mvc.perform(get("/api/diary/1990-11-01").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meals[0].note").value(nullValue()))
                .andExpect(jsonPath("$.meals[0].estimatedCalories").value(nullValue()));
    }

    @Test
    void json_invalidBodies_are400_andUnknownMeal_is404() throws Exception {
        Long id = addMeal(D1, "dinner");
        String path = "/api/diary/meals/" + id + "/details";

        for (String body : new String[]{
                "{\"note\":null,\"estimatedCalories\":900.5}",
                "{\"note\":null,\"estimatedCalories\":0}",
                "{\"note\":null,\"estimatedCalories\":10001}",
                "{\"note\":\"" + "a".repeat(501) + "\",\"estimatedCalories\":null}"}) {
            mvc.perform(put(path).with(user("admin")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/diary/meals/999999999/details").with(user("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\",\"estimatedCalories\":1}"))
                .andExpect(status().isNotFound());
    }
}
