CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    username   TEXT NOT NULL UNIQUE,
    password   TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Single admin user; password is bcrypt hash of APP_ADMIN_PASSWORD env var
-- seeded by Spring ApplicationRunner on first startup (see UserSeeder.java)
