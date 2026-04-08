-- V6__options_support.sql
-- Options trade support: add option columns to trades table

-- 1. Add new columns
ALTER TABLE trades ADD COLUMN asset_type VARCHAR(6) NOT NULL DEFAULT 'EQUITY';
ALTER TABLE trades ADD COLUMN option_type VARCHAR(4);
ALTER TABLE trades ADD COLUMN strike_price DECIMAL(12,4);
ALTER TABLE trades ADD COLUMN expiration_date DATE;
ALTER TABLE trades ADD COLUMN multiplier INTEGER NOT NULL DEFAULT 1;
ALTER TABLE trades ADD COLUMN linked_trade_id BIGINT;

-- 2. Expand side column to fit 'EXERCISE' (8 chars)
-- Drop the V4 inline CHECK first (Postgres auto-names it trades_side_check)
ALTER TABLE trades DROP CONSTRAINT IF EXISTS trades_side_check;
ALTER TABLE trades ALTER COLUMN side TYPE VARCHAR(8);

-- 3. Add new constraints
ALTER TABLE trades ADD CONSTRAINT trades_asset_type_check
    CHECK (asset_type IN ('EQUITY', 'OPTION'));
ALTER TABLE trades ADD CONSTRAINT trades_option_type_check
    CHECK (option_type IS NULL OR option_type IN ('CALL', 'PUT'));
ALTER TABLE trades ADD CONSTRAINT trades_linked_trade_fk
    FOREIGN KEY (linked_trade_id) REFERENCES trades(id);

-- 4. Index for contract identity lookups (lot matching)
CREATE INDEX idx_trades_contract
    ON trades (user_id, ticker, asset_type, option_type, strike_price, expiration_date);
