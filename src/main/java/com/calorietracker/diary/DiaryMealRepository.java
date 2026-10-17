package com.calorietracker.diary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DiaryMealRepository extends JpaRepository<DiaryMeal, Long> {

    /**
     * Total of the meals' calorie estimates for every day in {@code [from, to]} that has at least
     * one (days without an estimate are absent). One query for the whole range, whether or not
     * the meals have logged items; powers GET /api/diary/summary.
     */
    @Query("""
            SELECT new com.calorietracker.diary.DailyEstimateTotal(e.entryDate, SUM(m.estimatedCalories))
            FROM DiaryMeal m
            JOIN m.diaryEntry e
            WHERE e.entryDate BETWEEN :from AND :to
              AND m.estimatedCalories IS NOT NULL
            GROUP BY e.entryDate
            """)
    List<DailyEstimateTotal> sumEstimatedCaloriesByDate(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
