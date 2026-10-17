package com.calorietracker.eatingwindow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EatingWindowRepository extends JpaRepository<EatingWindow, Long> {

    Optional<EatingWindow> findByEntryDate(LocalDate date);

    /** Every window in {@code [from, to]}: one query for the whole summary range. */
    List<EatingWindow> findByEntryDateBetween(LocalDate from, LocalDate to);
}
