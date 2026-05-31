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
    public List<DiaryItem> getItems() { return items; }
}
