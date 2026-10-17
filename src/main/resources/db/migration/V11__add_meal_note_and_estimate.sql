-- Optional details on a meal, for food that cannot be logged item by item (e.g. eating out):
-- a short free-text note and a rough calorie estimate. The estimate counts toward the meal's and
-- the day's calories; it carries no macros. Both columns are nullable: most meals have neither.
-- The note's 500-character limit is enforced by the API.
-- Plain DDL on purpose: no CREATE INDEX CONCURRENTLY (it hangs inside Flyway's migration run).
ALTER TABLE diary_meals
    ADD COLUMN note               TEXT NULL,
    ADD COLUMN estimated_calories INT  NULL CHECK (estimated_calories BETWEEN 1 AND 10000);
