-- Photos attached to a meal (at most 6 per meal, enforced by the API). The image files live in
-- photo storage (S3 in prod, the local filesystem in dev); a row holds the storage keys of the
-- full-size JPEG and its thumbnail. width/height/bytes describe the full-size image and are
-- optional. Deleting a meal deletes its photo rows (the stored files are removed by the API's
-- photo delete endpoint, not by this cascade).
-- Plain DDL on purpose: no CREATE INDEX CONCURRENTLY (it hangs inside Flyway's migration run).
CREATE TABLE meal_photos (
    id            BIGSERIAL    PRIMARY KEY,
    diary_meal_id BIGINT       NOT NULL REFERENCES diary_meals(id) ON DELETE CASCADE,
    full_key      TEXT         NOT NULL,
    thumb_key     TEXT         NOT NULL,
    width         INT,
    height        INT,
    bytes         INT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_meal_photos_meal ON meal_photos(diary_meal_id);
