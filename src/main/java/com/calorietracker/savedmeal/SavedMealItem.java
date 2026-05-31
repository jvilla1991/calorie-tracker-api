package com.calorietracker.savedmeal;

import com.calorietracker.food.Food;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "saved_meal_items")
public class SavedMealItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_meal_id", nullable = false)
    private SavedMeal savedMeal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    @Column(name = "quantity_grams", nullable = false)
    private BigDecimal quantityGrams;

    public Long getId() { return id; }
    public SavedMeal getSavedMeal() { return savedMeal; }
    public void setSavedMeal(SavedMeal m) { this.savedMeal = m; }
    public Food getFood() { return food; }
    public void setFood(Food f) { this.food = f; }
    public BigDecimal getQuantityGrams() { return quantityGrams; }
    public void setQuantityGrams(BigDecimal q) { this.quantityGrams = q; }
}
