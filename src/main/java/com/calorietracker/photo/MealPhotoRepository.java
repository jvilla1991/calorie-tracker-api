package com.calorietracker.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MealPhotoRepository extends JpaRepository<MealPhoto, Long> {

    long countByDiaryMealId(Long diaryMealId);

    List<MealPhoto> findByDiaryMealIdOrderByIdAsc(Long diaryMealId);

    /**
     * Every photo of every meal on {@code date}, oldest (lowest id) first. One query for the whole
     * diary day, however many meals it has (no N+1).
     */
    @Query("""
            SELECT p FROM MealPhoto p
            JOIN DiaryMeal m ON m.id = p.diaryMealId
            JOIN m.diaryEntry e
            WHERE e.entryDate = :date
            ORDER BY p.id
            """)
    List<MealPhoto> findAllForDate(@Param("date") LocalDate date);
}
