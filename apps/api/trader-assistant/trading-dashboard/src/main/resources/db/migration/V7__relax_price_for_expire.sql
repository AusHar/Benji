-- V7__relax_price_for_expire.sql
-- Allow price_per_share = 0 for EXPIRE/EXERCISE trades

-- Drop the V4 inline CHECK (Postgres auto-names it trades_price_per_share_check)
ALTER TABLE trades DROP CONSTRAINT IF EXISTS trades_price_per_share_check;

-- Re-add with >= 0 instead of > 0
ALTER TABLE trades ADD CONSTRAINT trades_price_per_share_check
    CHECK (price_per_share >= 0);
