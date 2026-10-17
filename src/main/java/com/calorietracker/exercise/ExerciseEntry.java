package com.calorietracker.exercise;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One logged exercise on a calendar day, with the calories it burned. */
@Entity
@Table(name = "exercise_entries")
public class ExerciseEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "calories", nullable = false)
    private int calories;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate d) { this.entryDate = d; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public int getCalories() { return calories; }
    public void setCalories(int c) { this.calories = c; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
