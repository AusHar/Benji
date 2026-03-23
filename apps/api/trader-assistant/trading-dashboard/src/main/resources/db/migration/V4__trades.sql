CREATE TABLE trades (
    id              BIGSERIAL       PRIMARY KEY,
    ticker          VARCHAR(12)     NOT NULL,
    side            VARCHAR(4)      NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity        DECIMAL(12,4)   NOT NULL CHECK (quantity > 0),
    price_per_share DECIMAL(12,4)   NOT NULL CHECK (price_per_share > 0),
    trade_date      DATE            NOT NULL DEFAULT CURRENT_DATE,
    notes           TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trades_ticker ON trades (ticker);
CREATE INDEX idx_trades_trade_date ON trades (trade_date);
