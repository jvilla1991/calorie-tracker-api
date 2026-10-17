package com.calorietracker.exercise;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExerciseEntryRepository extends JpaRepository<ExerciseEntry, Long> {

    List<ExerciseEntry> findByEntryDateOrderByIdAsc(LocalDate date);

    /**
     * Calories burned per day for every day in {@code [from, to]} that has exercise
     * (days without exercise are absent). One query for the whole range; powers the summary.
     */
    @Query("""
            SELECT new com.calorietracker.exercise.DailyExerciseTotal(e.entryDate, SUM(e.calories))
            FROM ExerciseEntry e
            WHERE e.entryDate BETWEEN :from AND :to
            GROUP BY e.entryDate
            """)
    List<DailyExerciseTotal> sumCaloriesByDate(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
