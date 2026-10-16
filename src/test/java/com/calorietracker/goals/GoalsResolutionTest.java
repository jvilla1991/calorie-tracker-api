package com.calorietracker.goals;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for which calories goal applies on a given date. */
class GoalsResolutionTest {

    private static final LocalDate D1 = LocalDate.of(2026, 10, 1);

    private static DailyGoal goal(LocalDate effectiveFrom, int calories) {
        DailyGoal g = new DailyGoal();
        g.setEffectiveFrom(effectiveFrom);
        g.setCalories(calories);
        g.setProtein(BigDecimal.ONE);
        g.setCarbs(BigDecimal.ONE);
        g.setFat(BigDecimal.ONE);
        g.setFiber(BigDecimal.ONE);
        return g;
    }

    @Test
    void goalChangesMidRange() {
        List<DailyGoal> history = List.of(goal(D1.minusDays(30), 2000), goal(D1.plusDays(2), 1800));

        Map<LocalDate, Integer> goals = GoalsService.resolveCaloriesGoals(history, D1, D1.plusDays(3));

        assertThat(goals).containsExactly(
                Map.entry(D1, 2000),
                Map.entry(D1.plusDays(1), 2000),
                Map.entry(D1.plusDays(2), 1800),   // effective_from is inclusive
                Map.entry(D1.plusDays(3), 1800));
    }

    @Test
    void datesBeforeEveryGoal_useTheEarliestGoal() {
        List<DailyGoal> history = List.of(goal(D1.plusDays(5), 2200), goal(D1.plusDays(9), 2500));

        Map<LocalDate, Integer> goals = GoalsService.resolveCaloriesGoals(history, D1, D1.plusDays(1));

        assertThat(goals.values()).containsExactly(2200, 2200);
    }

    @Test
    void severalGoalsOnTheSameDate_lastSavedWins() {
        // History arrives ordered by (effective_from, id); a later row on the same day wins.
        List<DailyGoal> history = List.of(goal(D1, 2000), goal(D1, 2100), goal(D1, 1900));

        assertThat(GoalsService.resolveCaloriesGoals(history, D1, D1)).containsEntry(D1, 1900);
    }

    @Test
    void noGoalsAtAll_fallsBackToDefault() {
        assertThat(GoalsService.resolveCaloriesGoals(List.of(), D1, D1)).containsEntry(D1, 2000);
    }
}
