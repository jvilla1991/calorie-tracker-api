package com.calorietracker.diary;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryItemRepository extends JpaRepository<DiaryItem, Long> {}
