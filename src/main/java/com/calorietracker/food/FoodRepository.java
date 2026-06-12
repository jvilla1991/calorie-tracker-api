package com.calorietracker.food;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodRepository extends JpaRepository<Food, Long> {

    Optional<Food> findByBarcode(String barcode);

    /**
     * Name search restricted to a single source ("custom" or "openfoodfacts").
     * Full-text match first, falling back to a substring (ILIKE) match so partial
     * words still hit. Used to build the two search categories independently.
     */
    @Query(value = """
        SELECT * FROM foods
        WHERE source = :source
          AND (to_tsvector('english', name) @@ plainto_tsquery('english', :q)
               OR name ILIKE '%' || :q || '%')
        ORDER BY name
        LIMIT 20
        """, nativeQuery = true)
    List<Food> searchByNameAndSource(@Param("q") String query, @Param("source") String source);
}
