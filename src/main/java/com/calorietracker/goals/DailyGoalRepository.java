package com.calorietracker.goals;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyGoalRepository extends JpaRepository<DailyGoal, Long> {

    /**
     * The goal in effect on {@code today}: the latest {@code effective_from <= today}. Saving goals
     * inserts a new row dated today, so several rows can share a date; the most recently saved
     * (highest id) wins, matching how the daily summary resolves goals per date.
     */
    @Query("SELECT g FROM DailyGoal g WHERE g.effectiveFrom <= :today ORDER BY g.effectiveFrom DESC, g.id DESC LIMIT 1")
    Optional<DailyGoal> findCurrentGoal(@Param("today") LocalDate today);
}
