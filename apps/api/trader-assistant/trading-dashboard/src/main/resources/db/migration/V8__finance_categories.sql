-- V8__finance_categories.sql
-- Per-user finance categories with lazy seeding flag on the users table.

ALTER TABLE users ADD COLUMN category_seeded BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE finance_category (
    id          VARCHAR(36)  PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    slug        VARCHAR(64)  NOT NULL,
    label       VARCHAR(64)  NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_finance_category_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_finance_category_user_slug UNIQUE (user_id, slug)
);

CREATE INDEX idx_finance_category_user_id ON finance_category(user_id);
