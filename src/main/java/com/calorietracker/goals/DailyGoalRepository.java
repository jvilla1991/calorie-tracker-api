package com.calorietracker.goals;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyGoalRepository extends JpaRepository<DailyGoal, Long> {

    @Query("SELECT g FROM DailyGoal g WHERE g.effectiveFrom <= :today ORDER BY g.effectiveFrom DESC LIMIT 1")
    Optional<DailyGoal> findCurrentGoal(@Param("today") LocalDate today);
}
