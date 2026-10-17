package com.calorietracker.eatingwindow;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** When eating started and stopped on one calendar day. Either time may be null. */
@Entity
@Table(name = "eating_windows")
public class EatingWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_date", nullable = false, unique = true)
    private LocalDate entryDate;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate d) { this.entryDate = d; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant t) { this.startedAt = t; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant t) { this.endedAt = t; }
    public Instant getUpdatedAt() { return updatedAt; }
}
