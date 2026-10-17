package com.calorietracker.diary;

import com.calorietracker.diary.dto.DailySummaryDto;
import com.calorietracker.diary.dto.DiaryResponse;
import com.calorietracker.food.Food;
import com.calorietracker.goals.DailyGoal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end summary against real Postgres: the range fetch-join query, goal history query and
 * parity with the diary day view. {@code @Transactional} rolls every inserted row back.
 */
@SpringBootTest
@Transactional
class DiarySummaryIT {

    // Far in the past so it cannot collide with real diary dates.
    private static final LocalDate D1 = LocalDate.of(1990, 3, 1);

    @Autowired EntityManager em;
    @Autowired DiarySummaryService summaryService;
    @Autowired DiaryService diaryService;

    private Food food(String name, String calPer100g, String protPer100g) {
        Food f = new Food();
        f.setName(name);
        f.setSource("custom");
        f.setCaloriesPer100g(new BigDecimal(calPer100g));
        f.setProteinPer100g(new BigDecimal(protPer100g));
        f.setCarbsPer100g(BigDecimal.TEN);
        f.setFatPer100g(BigDecimal.ONE);
        em.persist(f);
        return f;
    }

    private DiaryEntry entry(LocalDate date) {
        return em.createQuery("SELECT e FROM DiaryEntry e WHERE e.entryDate = :d", DiaryEntry.class)
                .setParameter("d", date).getResultStream().findFirst()
                .orElseGet(() -> {
                    DiaryEntry e = new DiaryEntry();
                    e.setEntryDate(date);
                    em.persist(e);
                    return e;
                });
    }

    private void log(LocalDate date, String mealType, Food f, String grams) {
        DiaryMeal meal = new DiaryMeal();
        meal.setDiaryEntry(entry(date));
        meal.setMealType(mealType);
        em.persist(meal);
        DiaryItem i = new DiaryItem();
        i.setDiaryMeal(meal);
        i.setFood(f);
        i.setQuantityGrams(new BigDecimal(grams));
        em.persist(i);
    }

    /** A meal with only a rough calorie estimate and nothing logged (e.g. eating out). */
    private void estimate(LocalDate date, String mealType, int calories) {
        DiaryMeal meal = new DiaryMeal();
        meal.setDiaryEntry(entry(date));
        meal.setMealType(mealType);
        meal.setEstimatedCalories(calories);
        em.persist(meal);
    }

    private void goal(LocalDate effectiveFrom, int calories) {
        DailyGoal g = new DailyGoal();
        g.setEffectiveFrom(effectiveFrom);
        g.setCalories(calories);
        g.setProtein(BigDecimal.ONE);
        g.setCarbs(BigDecimal.ONE);
        g.setFat(BigDecimal.ONE);
        g.setFiber(BigDecimal.ONE);
        em.persist(g);
    }

    @Test
    void summarizesRangeWithZeroDaysGoalChangeAndDayViewParity() {
        em.createQuery("DELETE FROM DailyGoal").executeUpdate(); // isolate goal history (rolled back)
        goal(D1.minusDays(10), 2000);
        goal(D1.plusDays(2), 1700);

        Food oats = food("Oats", "389.33", "16.89");
        Food milk = food("Milk", "42", "3.4");
        log(D1, "breakfast", oats, "47.5");
        log(D1, "breakfast", milk, "240");
        log(D1, "dinner", oats, "13.3");
        estimate(D1, "lunch", 650);          // eaten out: an estimate, nothing logged
        log(D1.plusDays(3), "lunch", milk, "100");
        estimate(D1.plusDays(4), "dinner", 900);   // just outside the range: must not count
        em.flush();
        em.clear();

        List<DailySummaryDto> rows = summaryService.summarize(D1, D1.plusDays(3));

        assertThat(rows).extracting(DailySummaryDto::date)
                .containsExactly(D1, D1.plusDays(1), D1.plusDays(2), D1.plusDays(3));
        assertThat(rows).extracting(DailySummaryDto::goalCalories).containsExactly(2000, 2000, 1700, 1700);
        // 184.93 + 100.80 + 51.78 logged, plus the 650 estimate.
        assertThat(rows.get(0).calories()).isEqualByComparingTo("987.51");
        assertThat(rows.get(1).calories()).isEqualByComparingTo("0");
        assertThat(rows.get(2).calories()).isEqualByComparingTo("0");
        assertThat(rows.get(3).calories()).isEqualByComparingTo("42");

        DiaryResponse day1 = diaryService.getDay(D1);
        assertThat(rows.get(0).calories()).isEqualTo(day1.totals().getCalories());
        assertThat(rows.get(0).protein()).isEqualTo(day1.totals().getProtein());
        assertThat(rows.get(0).carbs()).isEqualTo(day1.totals().getCarbs());
        assertThat(rows.get(0).fat()).isEqualTo(day1.totals().getFat());
        assertThat(rows.get(0).fiber()).isEqualTo(day1.totals().getFiber());
    }
}
