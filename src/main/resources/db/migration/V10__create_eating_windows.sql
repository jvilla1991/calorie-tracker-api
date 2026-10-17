-- Eating window (intermittent fasting log): when eating started and stopped, at most one row per
-- calendar day. Either time may be missing while the day is in progress (started, not yet ended).
-- The UNIQUE constraint on entry_date also provides the index used for date-range queries.
-- Plain DDL on purpose: no CREATE INDEX CONCURRENTLY (it hangs inside Flyway's migration run).
CREATE TABLE eating_windows (
    id         BIGSERIAL    PRIMARY KEY,
    entry_date DATE         NOT NULL UNIQUE,
    started_at TIMESTAMPTZ,
    ended_at   TIMESTAMPTZ,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
