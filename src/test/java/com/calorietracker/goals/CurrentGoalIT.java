package com.calorietracker.goals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which goal row counts as "current", against real Postgres.
 * {@code @Transactional} rolls every inserted row back.
 */
@SpringBootTest
@Transactional
class CurrentGoalIT {

    // Far in the past so these rows cannot interact with real goals or the V4 seed row.
    private static final LocalDate D1 = LocalDate.of(1990, 5, 1);

    @Autowired GoalsService service;
    @Autowired DailyGoalRepository repo;

    @Test
    void twoSavesOnTheSameDay_getReturnsTheSecond() {
        service.update(request(2100));
        DailyGoal second = service.update(request(2300));
        repo.flush();

        DailyGoal current = service.getCurrent();
        assertThat(current.getId()).isEqualTo(second.getId());
        assertThat(current.getCalories()).isEqualTo(2300);
    }

    @Test
    void sameDateTie_highestIdWins_evenWhenTheOlderRowWasSavedFirst() {
        DailyGoal first = repo.save(goal(1800, D1));
        DailyGoal second = repo.save(goal(1900, D1));
        repo.flush();
        assertThat(second.getId()).isGreaterThan(first.getId());

        assertThat(repo.findCurrentGoal(D1.plusDays(3))).get()
                .extracting(DailyGoal::getId).isEqualTo(second.getId());
    }

    @Test
    void newerEffectiveDateWins_overOlderOne_regardlessOfId() {
        // Newer date saved FIRST (lower id), older date saved second: date must beat id.
        DailyGoal newer = repo.save(goal(2200, D1.plusDays(10)));
        repo.save(goal(1700, D1));
        repo.flush();

        assertThat(repo.findCurrentGoal(D1.plusDays(20))).get()
                .extracting(DailyGoal::getId).isEqualTo(newer.getId());
    }

    @Test
    void futureDatedRow_isIgnored() {
        DailyGoal inEffect = repo.save(goal(2000, D1));
        repo.save(goal(9999, D1.plusDays(5)));
        repo.flush();

        assertThat(repo.findCurrentGoal(D1.plusDays(2))).get()
                .extracting(DailyGoal::getId).isEqualTo(inEffect.getId());
    }

    @Test
    void futureDatedRow_isIgnoredByGetCurrent() {
        DailyGoal saved = service.update(request(2400));
        repo.save(goal(9999, LocalDate.now().plusDays(1)));
        repo.flush();

        assertThat(service.getCurrent().getId()).isEqualTo(saved.getId());
    }

    private static GoalsRequest request(int calories) {
        GoalsRequest r = new GoalsRequest();
        r.setCalories(calories);
        r.setProtein(BigDecimal.valueOf(150));
        r.setCarbs(BigDecimal.valueOf(200));
        r.setFat(BigDecimal.valueOf(65));
        r.setFiber(BigDecimal.valueOf(30));
        return r;
    }

    private static DailyGoal goal(int calories, LocalDate effectiveFrom) {
        DailyGoal g = new DailyGoal();
        g.setCalories(calories);
        g.setProtein(BigDecimal.valueOf(150));
        g.setCarbs(BigDecimal.valueOf(200));
        g.setFat(BigDecimal.valueOf(65));
        g.setFiber(BigDecimal.valueOf(30));
        g.setEffectiveFrom(effectiveFrom);
        return g;
    }
}
