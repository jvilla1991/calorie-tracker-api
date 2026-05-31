package com.calorietracker.diary;

import com.calorietracker.food.Food;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "diary_items")
public class DiaryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_meal_id", nullable = false)
    private DiaryMeal diaryMeal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    @Column(name = "quantity_grams", nullable = false)
    private BigDecimal quantityGrams;

    @Column(name = "added_at", nullable = false, updatable = false)
    private OffsetDateTime addedAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public DiaryMeal getDiaryMeal() { return diaryMeal; }
    public void setDiaryMeal(DiaryMeal m) { this.diaryMeal = m; }
    public Food getFood() { return food; }
    public void setFood(Food f) { this.food = f; }
    public BigDecimal getQuantityGrams() { return quantityGrams; }
    public void setQuantityGrams(BigDecimal q) { this.quantityGrams = q; }
    public OffsetDateTime getAddedAt() { return addedAt; }
}
