-- Exercise log: any number of entries per calendar day, each with calories burned.
-- Plain DDL on purpose: no CREATE INDEX CONCURRENTLY (it hangs inside Flyway's migration run).
CREATE TABLE exercise_entries (
    id         BIGSERIAL    PRIMARY KEY,
    entry_date DATE         NOT NULL,
    name       TEXT         NOT NULL,
    calories   INT          NOT NULL CHECK (calories > 0),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_exercise_entries_entry_date ON exercise_entries (entry_date);
