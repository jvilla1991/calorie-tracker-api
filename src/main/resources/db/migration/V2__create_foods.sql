CREATE TABLE foods (
    id                  BIGSERIAL PRIMARY KEY,
    barcode             TEXT,
    name                TEXT NOT NULL,
    brand               TEXT,
    calories_per_100g   NUMERIC(8,2),
    protein_per_100g    NUMERIC(8,2),
    carbs_per_100g      NUMERIC(8,2),
    fat_per_100g        NUMERIC(8,2),
    fiber_per_100g      NUMERIC(8,2),
    source              TEXT NOT NULL DEFAULT 'custom',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_foods_barcode ON foods(barcode) WHERE barcode IS NOT NULL;
CREATE INDEX idx_foods_name    ON foods USING gin(to_tsvector('english', name));
