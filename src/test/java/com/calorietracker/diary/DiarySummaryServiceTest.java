package com.calorietracker.diary;

import com.calorietracker.diary.dto.DailySummaryDto;
import com.calorietracker.diary.dto.DiaryResponse;
import com.calorietracker.eatingwindow.EatingWindowService;
import com.calorietracker.eatingwindow.dto.EatingWindowResponse;
import com.calorietracker.exercise.ExerciseService;
import com.calorietracker.food.Food;
import com.calorietracker.goals.GoalsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for {@link DiarySummaryService} (Mockito, no DB). */
@ExtendWith(MockitoExtension.class)
class DiarySummaryServiceTest {

    private static final LocalDate D1 = LocalDate.of(2026, 10, 1);

    @Mock DiaryItemRepository itemRepo;
    @Mock DiaryMealRepository mealRepo;
    @Mock GoalsService goalsService;
    @Mock ExerciseService exerciseService;
    @Mock EatingWindowService eatingWindowService;

    @InjectMocks DiarySummaryService service;

    private static Food food(String cal, String prot, String carb, String fat, String fib) {
        Food f = new Food();
        f.setName("f");
        f.setCaloriesPer100g(new BigDecimal(cal));
        f.setProteinPer100g(new BigDecimal(prot));
        f.setCarbsPer100g(new BigDecimal(carb));
        f.setFatPer100g(new BigDecimal(fat));
        f.setFiberPer100g(fib == null ? null : new BigDecimal(fib));
        return f;
    }

    private static DiaryEntry entry(LocalDate date) {
        DiaryEntry e = new DiaryEntry();
        e.setEntryDate(date);
        return e;
    }

    private static DiaryItem item(DiaryEntry entry, Food food, String grams) {
        DiaryMeal meal = new DiaryMeal();
        meal.setDiaryEntry(entry);
        meal.setMealType("lunch");
        entry.getMeals().add(meal);
        DiaryItem i = new DiaryItem();
        i.setDiaryMeal(meal);
        i.setFood(food);
        i.setQuantityGrams(new BigDecimal(grams));
        meal.getItems().add(i);
        return i;
    }

    private void goalsConstant(LocalDate from, LocalDate to, int calories) {
        Map<LocalDate, Integer> m = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) m.put(d, calories);
        when(goalsService.caloriesGoalsByDate(from, to)).thenReturn(m);
    }

    @Test
    void fillsUnloggedDaysWithZeros_inAscendingOrder() {
        DiaryEntry day2 = entry(D1.plusDays(1));
        when(itemRepo.findAllInDateRange(D1, D1.plusDays(2)))
                .thenReturn(List.of(item(day2, food("200", "10", "20", "5", "2"), "150")));
        goalsConstant(D1, D1.plusDays(2), 2000);

        List<DailySummaryDto> rows = service.summarize(D1, D1.plusDays(2));

        assertThat(rows).extracting(DailySummaryDto::date)
                .containsExactly(D1, D1.plusDays(1), D1.plusDays(2));
        assertThat(rows.get(0).calories()).isEqualByComparingTo("0");
        assertThat(rows.get(0).fiber()).isEqualByComparingTo("0");
        assertThat(rows.get(1).calories()).isEqualByComparingTo("300");
        assertThat(rows.get(1).protein()).isEqualByComparingTo("15");
        assertThat(rows.get(2).calories()).isEqualByComparingTo("0");
        assertThat(rows).extracting(DailySummaryDto::goalCalories).containsOnly(2000);
    }

    @Test
    void totalsMatchTheDiaryDayViewExactly_includingPerItemRounding() {
        // 33.33g of a 123.45 kcal food rounds per item; summing then rounding would differ.
        DiaryEntry day = entry(D1);
        Food odd = food("123.45", "7.77", "3.33", "1.11", null);
        DiaryItem a = item(day, odd, "33.33");
        DiaryItem b = item(day, odd, "66.67");
        DiaryItem c = item(day, food("52", "0.3", "14", "0.2", "2.4"), "182");
        when(itemRepo.findAllInDateRange(D1, D1)).thenReturn(List.of(a, b, c));
        goalsConstant(D1, D1, 2000);

        DailySummaryDto row = service.summarize(D1, D1).get(0);
        DiaryResponse dayView = DiaryResponse.from(day);

        assertThat(row.calories()).isEqualTo(dayView.totals().getCalories());
        assertThat(row.protein()).isEqualTo(dayView.totals().getProtein());
        assertThat(row.carbs()).isEqualTo(dayView.totals().getCarbs());
        assertThat(row.fat()).isEqualTo(dayView.totals().getFat());
        assertThat(row.fiber()).isEqualTo(dayView.totals().getFiber());
    }

    @Test
    void mealEstimatesAddCaloriesButNoMacros_fromOneRangeQuery() {
        DiaryEntry day2 = entry(D1.plusDays(1));
        when(itemRepo.findAllInDateRange(D1, D1.plusDays(2)))
                .thenReturn(List.of(item(day2, food("200", "10", "20", "5", "2"), "150")));
        when(mealRepo.sumEstimatedCaloriesByDate(D1, D1.plusDays(2))).thenReturn(List.of(
                new DailyEstimateTotal(D1, 900L),                 // an estimate and nothing logged
                new DailyEstimateTotal(D1.plusDays(1), 450L)));
        goalsConstant(D1, D1.plusDays(2), 2000);

        List<DailySummaryDto> rows = service.summarize(D1, D1.plusDays(2));

        assertThat(rows.get(0).calories()).isEqualByComparingTo("900");
        assertThat(rows.get(0).protein()).isEqualByComparingTo("0");
        assertThat(rows.get(1).calories()).isEqualByComparingTo("750");   // 300 logged + 450 estimated
        assertThat(rows.get(1).protein()).isEqualByComparingTo("15");
        assertThat(rows.get(1).carbs()).isEqualByComparingTo("30");
        assertThat(rows.get(2).calories()).isEqualByComparingTo("0");
        verify(mealRepo, times(1)).sumEstimatedCaloriesByDate(D1, D1.plusDays(2));
        verifyNoMoreInteractions(mealRepo);
    }

    @Test
    void totalsWithEstimatesMatchTheDiaryDayViewExactly() {
        DiaryEntry day = entry(D1);
        DiaryItem logged = item(day, food("123.45", "7.77", "3.33", "1.11", null), "33.33");
        logged.getDiaryMeal().setEstimatedCalories(250);   // a logged meal that also has an estimate
        DiaryMeal estimateOnly = new DiaryMeal();
        estimateOnly.setDiaryEntry(day);
        estimateOnly.setMealType("dinner");
        estimateOnly.setEstimatedCalories(900);
        day.getMeals().add(estimateOnly);
        when(itemRepo.findAllInDateRange(D1, D1)).thenReturn(List.of(logged));
        when(mealRepo.sumEstimatedCaloriesByDate(D1, D1)).thenReturn(List.of(new DailyEstimateTotal(D1, 1150L)));
        goalsConstant(D1, D1, 2000);

        DailySummaryDto row = service.summarize(D1, D1).get(0);
        DiaryResponse dayView = DiaryResponse.from(day);

        assertThat(row.calories()).isEqualByComparingTo("1191.15");      // 41.15 logged + 250 + 900
        assertThat(row.calories()).isEqualTo(dayView.totals().getCalories());
        assertThat(row.protein()).isEqualTo(dayView.totals().getProtein());
        assertThat(row.carbs()).isEqualTo(dayView.totals().getCarbs());
        assertThat(row.fat()).isEqualTo(dayView.totals().getFat());
        assertThat(row.fiber()).isEqualTo(dayView.totals().getFiber());
    }

    @Test
    void goalCaloriesComeFromTheGoalInEffectEachDay() {
        when(itemRepo.findAllInDateRange(any(), any())).thenReturn(List.of());
        Map<LocalDate, Integer> goals = new LinkedHashMap<>();
        goals.put(D1, 2000);
        goals.put(D1.plusDays(1), 1800);
        when(goalsService.caloriesGoalsByDate(D1, D1.plusDays(1))).thenReturn(goals);

        assertThat(service.summarize(D1, D1.plusDays(1)))
                .extracting(DailySummaryDto::goalCalories).containsExactly(2000, 1800);
    }

    @Test
    void exerciseCaloriesComeFromOneRangeQuery_zeroOnDaysWithout() {
        when(itemRepo.findAllInDateRange(any(), any())).thenReturn(List.of());
        goalsConstant(D1, D1.plusDays(2), 2000);
        when(exerciseService.caloriesByDate(D1, D1.plusDays(2))).thenReturn(Map.of(D1.plusDays(1), 450));

        assertThat(service.summarize(D1, D1.plusDays(2)))
                .extracting(DailySummaryDto::exerciseCalories).containsExactly(0, 450, 0);
        verify(exerciseService, times(1)).caloriesByDate(D1, D1.plusDays(2));
        verifyNoMoreInteractions(exerciseService);
    }

    @Test
    void eatingWindowComesFromOneRangeQuery_nullOnDaysWithout() {
        when(itemRepo.findAllInDateRange(any(), any())).thenReturn(List.of());
        goalsConstant(D1, D1.plusDays(1), 2000);
        Instant start = Instant.parse("2026-10-02T16:20:00Z");
        when(eatingWindowService.byDate(D1, D1.plusDays(1)))
                .thenReturn(Map.of(D1.plusDays(1), new EatingWindowResponse(start, null)));

        List<DailySummaryDto> rows = service.summarize(D1, D1.plusDays(1));

        assertThat(rows.get(0).eatingStartedAt()).isNull();
        assertThat(rows.get(0).eatingEndedAt()).isNull();
        assertThat(rows.get(1).eatingStartedAt()).isEqualTo(start);
        assertThat(rows.get(1).eatingEndedAt()).isNull();
        verify(eatingWindowService, times(1)).byDate(D1, D1.plusDays(1));
        verifyNoMoreInteractions(eatingWindowService);
    }

    @Test
    void singleDayRange_isAllowed() {
        when(itemRepo.findAllInDateRange(D1, D1)).thenReturn(List.of());
        goalsConstant(D1, D1, 2000);

        assertThat(service.summarize(D1, D1)).hasSize(1);
    }

    @Test
    void fromAfterTo_is400() {
        assertThatThrownBy(() -> service.summarize(D1.plusDays(1), D1))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(itemRepo, goalsService);
    }

    @Test
    void rangeOf366DaysIsAllowed_367IsRejected() {
        when(itemRepo.findAllInDateRange(any(), any())).thenReturn(List.of());
        when(goalsService.caloriesGoalsByDate(any(), any())).thenAnswer(inv -> {
            Map<LocalDate, Integer> m = new LinkedHashMap<>();
            for (LocalDate d = inv.getArgument(0); !d.isAfter(inv.getArgument(1)); d = d.plusDays(1)) {
                m.put(d, 2000);
            }
            return m;
        });

        assertThat(service.summarize(D1, D1.plusDays(365))).hasSize(366);
        assertThatThrownBy(() -> service.summarize(D1, D1.plusDays(366)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void usesOneItemQueryForTheWholeRange() {
        when(itemRepo.findAllInDateRange(any(), any())).thenReturn(List.of());
        goalsConstant(D1, D1.plusDays(29), 2000);

        service.summarize(D1, D1.plusDays(29));

        verify(itemRepo, times(1)).findAllInDateRange(D1, D1.plusDays(29));
        verifyNoMoreInteractions(itemRepo);
    }
}
