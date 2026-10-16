package com.calorietracker.weighin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeighInRepository extends JpaRepository<WeighIn, Long> {

    Optional<WeighIn> findByWeighDate(LocalDate date);

    List<WeighIn> findAllByOrderByWeighDateAsc();

    List<WeighIn> findByWeighDateBetweenOrderByWeighDateAsc(LocalDate from, LocalDate to);

    List<WeighIn> findByWeighDateGreaterThanEqualOrderByWeighDateAsc(LocalDate from);

    List<WeighIn> findByWeighDateLessThanEqualOrderByWeighDateAsc(LocalDate to);
}
