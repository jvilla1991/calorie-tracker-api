package com.calorietracker.diary;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "diary_meals")
public class DiaryMeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_entry_id", nullable = false)
    private DiaryEntry diaryEntry;

    @Column(name = "meal_type", nullable = false)
    private String mealType;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Free-text note (e.g. what was eaten out), at most 500 characters; null when none. */
    @Column(name = "note")
    private String note;

    /** Rough calorie estimate, 1-10000; counts toward the meal's calories. Null when none. */
    @Column(name = "estimated_calories")
    private Integer estimatedCalories;

    @OneToMany(mappedBy = "diaryMeal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("addedAt ASC")
    private List<DiaryItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public DiaryEntry getDiaryEntry() { return diaryEntry; }
    public void setDiaryEntry(DiaryEntry e) { this.diaryEntry = e; }
    public String getMealType() { return mealType; }
    public void setMealType(String t) { this.mealType = t; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int o) { this.displayOrder = o; }
    public String getNote() { return note; }
    public void setNote(String n) { this.note = n; }
    public Integer getEstimatedCalories() { return estimatedCalories; }
    public void setEstimatedCalories(Integer c) { this.estimatedCalories = c; }
    public List<DiaryItem> getItems() { return items; }
}
