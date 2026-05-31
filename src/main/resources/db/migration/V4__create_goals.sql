CREATE TABLE daily_goals (
    id             BIGSERIAL    PRIMARY KEY,
    calories       INT          NOT NULL,
    protein        NUMERIC(8,2) NOT NULL,
    carbs          NUMERIC(8,2) NOT NULL,
    fat            NUMERIC(8,2) NOT NULL,
    fiber          NUMERIC(8,2) NOT NULL,
    effective_from DATE         NOT NULL DEFAULT CURRENT_DATE
);

-- Seed sensible defaults
INSERT INTO daily_goals (calories, protein, carbs, fat, fiber, effective_from)
VALUES (2000, 150, 200, 65, 30, CURRENT_DATE);
