-- Body-weight log: at most one weigh-in per calendar day (logging a day again replaces it).
-- Always stored in kilograms; the frontend converts to/from pounds for display.
-- The UNIQUE constraint on weigh_date also provides the index used for date-range queries.
-- Plain DDL on purpose: no CREATE INDEX CONCURRENTLY (it hangs inside Flyway's migration run).
CREATE TABLE weigh_ins (
    id         BIGSERIAL     PRIMARY KEY,
    weigh_date DATE          NOT NULL UNIQUE,
    weight_kg  NUMERIC(6,3)  NOT NULL CHECK (weight_kg > 0),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
