package com.calorietracker.diary;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DiaryItemRepository extends JpaRepository<DiaryItem, Long> {

    /**
     * Returns the most-recent DiaryItem for each distinct food, ordered by addedAt DESC.
     * Used to power the "Recent foods" picker in the front-end.
     * The inner subquery selects the highest item id per food (i.e. the latest log),
     * then the outer query fetches those full rows and sorts them.
     */
    @Query("""
            SELECT i FROM DiaryItem i
            WHERE i.id IN (
                SELECT MAX(i2.id) FROM DiaryItem i2 GROUP BY i2.food
            )
            ORDER BY i.addedAt DESC
            """)
    List<DiaryItem> findRecentDistinctFoods(Pageable pageable);
}
