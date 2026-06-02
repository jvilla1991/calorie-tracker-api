-- Support the GET /api/foods/recent query efficiently.
--
-- findRecentDistinctFoods runs:
--   SELECT MAX(id) FROM diary_items GROUP BY food_id   ← needs food_id index
--   ORDER BY added_at DESC                             ← needs added_at index
--
-- Without these, both operations full-scan diary_items.
-- Concurrent creation avoids any table lock during migration.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_diary_items_food
    ON diary_items (food_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_diary_items_added_at
    ON diary_items (added_at DESC);
