package com.calorietracker.goals;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "daily_goals")
public class DailyGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int calories;

    @Column(nullable = false)
    private BigDecimal protein;

    @Column(nullable = false)
    private BigDecimal carbs;

    @Column(nullable = false)
    private BigDecimal fat;

    @Column(nullable = false)
    private BigDecimal fiber;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.now();

    public Long getId() { return id; }
    public int getCalories() { return calories; }
    public void setCalories(int c) { this.calories = c; }
    public BigDecimal getProtein() { return protein; }
    public void setProtein(BigDecimal p) { this.protein = p; }
    public BigDecimal getCarbs() { return carbs; }
    public void setCarbs(BigDecimal c) { this.carbs = c; }
    public BigDecimal getFat() { return fat; }
    public void setFat(BigDecimal f) { this.fat = f; }
    public BigDecimal getFiber() { return fiber; }
    public void setFiber(BigDecimal f) { this.fiber = f; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate d) { this.effectiveFrom = d; }
}
