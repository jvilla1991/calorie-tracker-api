CREATE TABLE saved_meals (
    id         BIGSERIAL    PRIMARY KEY,
    name       TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE saved_meal_items (
    id             BIGSERIAL    PRIMARY KEY,
    saved_meal_id  BIGINT       NOT NULL REFERENCES saved_meals(id) ON DELETE CASCADE,
    food_id        BIGINT       NOT NULL REFERENCES foods(id),
    quantity_grams NUMERIC(8,2) NOT NULL
);

CREATE INDEX idx_saved_meal_items_meal ON saved_meal_items(saved_meal_id);
