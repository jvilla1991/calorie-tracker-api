package com.calorietracker.diary;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

import java.util.List;

public interface DiaryItemRepository extends JpaRepository<DiaryItem, Long> {

    /**
     * Returns the most-recent DiaryItem for each distinct food, ordered by addedAt DESC.
     * Paged via {@code pageable}; the secondary {@code id DESC} sort breaks ties between
     * items with the same timestamp so page boundaries are deterministic.
     * Used to power the "Recent foods" picker in the front-end.
     * The inner subquery selects the highest item id per food (i.e. the latest log),
     * then the outer query fetches those full rows and sorts them.
     */
    @Query("""
            SELECT i FROM DiaryItem i
            WHERE i.id IN (
                SELECT MAX(i2.id) FROM DiaryItem i2 GROUP BY i2.food
            )
            ORDER BY i.addedAt DESC, i.id DESC
            """)
    List<DiaryItem> findRecentDistinctFoods(Pageable pageable);

    /**
     * Every logged item whose diary date falls in {@code [from, to]}, with its food, meal and
     * entry fetched in the same query (one round trip for the whole range, no N+1).
     * Powers GET /api/diary/summary.
     */
    @Query("""
            SELECT i FROM DiaryItem i
            JOIN FETCH i.food
            JOIN FETCH i.diaryMeal m
            JOIN FETCH m.diaryEntry e
            WHERE e.entryDate BETWEEN :from AND :to
            """)
    List<DiaryItem> findAllInDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
