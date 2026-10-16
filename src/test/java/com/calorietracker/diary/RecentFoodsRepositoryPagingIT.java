package com.calorietracker.diary;

import com.calorietracker.food.Food;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@code findRecentDistinctFoods} against real Postgres. Every test is
 * {@code @Transactional}, so the inserted rows are rolled back afterwards.
 */
@SpringBootTest
@Transactional
class RecentFoodsRepositoryPagingIT {

    @Autowired EntityManager em;
    @Autowired DiaryItemRepository itemRepo;

    private DiaryMeal meal;

    @BeforeEach
    void setUp() {
        // Start from an empty diary inside this transaction so pre-existing rows can't interfere.
        em.createQuery("DELETE FROM DiaryItem").executeUpdate();

        DiaryEntry entry = new DiaryEntry();
        entry.setEntryDate(LocalDate.of(1999, 1, 1));
        em.persist(entry);
        meal = new DiaryMeal();
        meal.setDiaryEntry(entry);
        meal.setMealType("lunch");
        em.persist(meal);
    }

    private Food food(String name) {
        Food f = new Food();
        f.setName(name);
        f.setSource("custom");
        em.persist(f);
        return f;
    }

    private void log(Food f, int grams) {
        DiaryItem i = new DiaryItem();
        i.setDiaryMeal(meal);
        i.setFood(f);
        i.setQuantityGrams(BigDecimal.valueOf(grams));
        em.persist(i);
    }

    @Test
    void pagesAreDistinctPerFood_nonOverlapping_andMostRecentFirst() {
        Food a = food("A"), b = food("B"), c = food("C");
        log(a, 100);
        log(b, 100);
        log(c, 100);
        log(a, 250);   // A logged again -> most recent, and its latest quantity wins
        em.flush();

        List<DiaryItem> page0 = itemRepo.findRecentDistinctFoods(PageRequest.of(0, 2));
        List<DiaryItem> page1 = itemRepo.findRecentDistinctFoods(PageRequest.of(1, 2));
        List<DiaryItem> page2 = itemRepo.findRecentDistinctFoods(PageRequest.of(2, 2));

        assertThat(page0).extracting(i -> i.getFood().getName()).containsExactly("A", "C");
        assertThat(page0.get(0).getQuantityGrams()).isEqualByComparingTo("250");
        assertThat(page1).extracting(i -> i.getFood().getName()).containsExactly("B");
        assertThat(page2).isEmpty();
    }
}
