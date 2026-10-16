package com.calorietracker.weighin;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One body-weight reading per calendar day, always in kilograms. */
@Entity
@Table(name = "weigh_ins")
public class WeighIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "weigh_date", nullable = false, unique = true)
    private LocalDate weighDate;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public LocalDate getWeighDate() { return weighDate; }
    public void setWeighDate(LocalDate d) { this.weighDate = d; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal w) { this.weightKg = w; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
