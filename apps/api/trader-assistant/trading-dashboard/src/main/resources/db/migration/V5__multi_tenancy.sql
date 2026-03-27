-- V5__multi_tenancy.sql
-- Multi-tenancy: users table + user_id FK on all existing tables

-- 1. Create users table
CREATE TABLE users (
    id           BIGSERIAL    PRIMARY KEY,
    api_key      VARCHAR(64)  NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    is_demo      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_admin     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 2. Seed owner and demo users
INSERT INTO users (api_key, display_name, is_admin, is_demo)
VALUES ('owner-api-key-change-me', 'Owner', TRUE, FALSE);

INSERT INTO users (api_key, display_name, is_admin, is_demo)
VALUES ('demo', 'Demo User', FALSE, TRUE);

-- 3. portfolio_position: add user_id, replace UNIQUE(ticker) with UNIQUE(user_id, ticker)
ALTER TABLE portfolio_position ADD COLUMN user_id BIGINT;
UPDATE portfolio_position SET user_id = (SELECT id FROM users WHERE is_admin = TRUE);
ALTER TABLE portfolio_position ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE portfolio_position
    ADD CONSTRAINT fk_portfolio_position_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_portfolio_position_user_id ON portfolio_position(user_id);
ALTER TABLE portfolio_position DROP CONSTRAINT uq_portfolio_position_ticker;
ALTER TABLE portfolio_position
    ADD CONSTRAINT uq_portfolio_position_user_ticker UNIQUE (user_id, ticker);

-- 4. finance_transaction: add user_id
ALTER TABLE finance_transaction ADD COLUMN user_id BIGINT;
UPDATE finance_transaction SET user_id = (SELECT id FROM users WHERE is_admin = TRUE);
ALTER TABLE finance_transaction ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE finance_transaction
    ADD CONSTRAINT fk_finance_transaction_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_finance_transaction_user_id ON finance_transaction(user_id);

-- 5. journal_entries: add user_id, replace UNIQUE(entry_date) with UNIQUE(user_id, entry_date)
ALTER TABLE journal_entries ADD COLUMN user_id BIGINT;
UPDATE journal_entries SET user_id = (SELECT id FROM users WHERE is_admin = TRUE);
ALTER TABLE journal_entries ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE journal_entries
    ADD CONSTRAINT fk_journal_entries_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_journal_entries_user_id ON journal_entries(user_id);
ALTER TABLE journal_entries DROP CONSTRAINT uq_journal_entries_entry_date;
ALTER TABLE journal_entries
    ADD CONSTRAINT uq_journal_entries_user_entry_date UNIQUE (user_id, entry_date);

-- 6. journal_goals: add user_id
ALTER TABLE journal_goals ADD COLUMN user_id BIGINT;
UPDATE journal_goals SET user_id = (SELECT id FROM users WHERE is_admin = TRUE);
ALTER TABLE journal_goals ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE journal_goals
    ADD CONSTRAINT fk_journal_goals_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_journal_goals_user_id ON journal_goals(user_id);

-- 7. trades: add user_id with composite index
ALTER TABLE trades ADD COLUMN user_id BIGINT;
UPDATE trades SET user_id = (SELECT id FROM users WHERE is_admin = TRUE);
ALTER TABLE trades ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE trades
    ADD CONSTRAINT fk_trades_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE INDEX idx_trades_user_id_ticker ON trades(user_id, ticker);
