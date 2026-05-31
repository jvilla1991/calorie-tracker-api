CREATE TABLE diary_entries (
    id         BIGSERIAL PRIMARY KEY,
    entry_date DATE NOT NULL UNIQUE
);

CREATE TABLE diary_meals (
    id             BIGSERIAL PRIMARY KEY,
    diary_entry_id BIGINT      NOT NULL REFERENCES diary_entries(id) ON DELETE CASCADE,
    meal_type      TEXT        NOT NULL,
    display_order  INT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_diary_meals_entry ON diary_meals(diary_entry_id);

CREATE TABLE diary_items (
    id             BIGSERIAL PRIMARY KEY,
    diary_meal_id  BIGINT          NOT NULL REFERENCES diary_meals(id) ON DELETE CASCADE,
    food_id        BIGINT          NOT NULL REFERENCES foods(id),
    quantity_grams NUMERIC(8,2)    NOT NULL,
    added_at       TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_diary_items_meal ON diary_items(diary_meal_id);
