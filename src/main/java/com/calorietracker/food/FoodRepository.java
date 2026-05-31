package com.calorietracker.food;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodRepository extends JpaRepository<Food, Long> {

    Optional<Food> findByBarcode(String barcode);

    @Query(value = """
        SELECT * FROM foods
        WHERE to_tsvector('english', name) @@ plainto_tsquery('english', :q)
           OR name ILIKE '%' || :q || '%'
        ORDER BY name
        LIMIT 20
        """, nativeQuery = true)
    List<Food> searchByName(@Param("q") String query);
}
