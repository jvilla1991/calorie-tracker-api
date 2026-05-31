package com.calorietracker.savedmeal;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "saved_meals")
public class SavedMeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "savedMeal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SavedMealItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<SavedMealItem> getItems() { return items; }
}
