-- V9__csv_import_schema.sql
-- CSV import: account tagging, dedup keys, fractional share precision

-- trades: account column and dedup key
ALTER TABLE trades ADD COLUMN account VARCHAR(20);
ALTER TABLE trades ADD COLUMN import_dedup_key VARCHAR(64);
CREATE UNIQUE INDEX idx_trades_import_dedup_key
    ON trades(import_dedup_key);

-- Extend qty and price precision to handle DRIP fractional shares (up to 6 dp)
ALTER TABLE trades ALTER COLUMN quantity TYPE DECIMAL(16,6);
ALTER TABLE trades ALTER COLUMN price_per_share TYPE DECIMAL(16,6);

-- finance_transaction: account column and dedup key
ALTER TABLE finance_transaction ADD COLUMN account VARCHAR(20);
ALTER TABLE finance_transaction ADD COLUMN import_dedup_key VARCHAR(64);
CREATE UNIQUE INDEX idx_finance_transaction_import_dedup_key
    ON finance_transaction(import_dedup_key);
