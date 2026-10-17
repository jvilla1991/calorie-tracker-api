package com.calorietracker.photo;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One photo attached to a diary meal. The image files live in {@link PhotoStorage}; this row holds
 * their storage keys. {@code width}, {@code height} and {@code bytes} describe the full-size
 * image and are optional.
 */
@Entity
@Table(name = "meal_photos")
public class MealPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "diary_meal_id", nullable = false)
    private Long diaryMealId;

    @Column(name = "full_key", nullable = false)
    private String fullKey;

    @Column(name = "thumb_key", nullable = false)
    private String thumbKey;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "bytes")
    private Integer bytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getDiaryMealId() { return diaryMealId; }
    public void setDiaryMealId(Long id) { this.diaryMealId = id; }
    public String getFullKey() { return fullKey; }
    public void setFullKey(String k) { this.fullKey = k; }
    public String getThumbKey() { return thumbKey; }
    public void setThumbKey(String k) { this.thumbKey = k; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer w) { this.width = w; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer h) { this.height = h; }
    public Integer getBytes() { return bytes; }
    public void setBytes(Integer b) { this.bytes = b; }
    public Instant getCreatedAt() { return createdAt; }
}
