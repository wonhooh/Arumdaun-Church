CREATE TABLE IF NOT EXISTS clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id VARCHAR(64) NOT NULL DEFAULT '',
    korean_name VARCHAR(64) NOT NULL,
    surname VARCHAR(64)NOT NULL DEFAULT '',
    given_name VARCHAR(64) NOT NULL DEFAULT '',
    middle_name VARCHAR(64) NOT NULL DEFAULT '',
    phone1 VARCHAR(64) NOT NULL DEFAULT '',
    phone2 VARCHAR(64) NOT NULL DEFAULT '',
    street_1 VARCHAR(256) NOT NULL DEFAULT '',
    street_2 VARCHAR(256) NOT NULL DEFAULT '',
    city VARCHAR(64) NOT NULL DEFAULT '',
    state VARCHAR(64) NOT NULL DEFAULT '',
    zip_code VARCHAR(64) NOT NULL DEFAULT '',
    deleted_date DATE
);

CREATE TABLE IF NOT EXISTS lots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES clients(id),
    lot_number TEXT NOT NULL UNIQUE,
    price INTEGER NOT NULL,
    information TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES clients(id),
    lot_id UUID REFERENCES lots(id),
    transaction_type TEXT NOT NULL,
    lot_numbers TEXT[] NOT NULL DEFAULT '{}',
    total_price INTEGER NOT NULL DEFAULT 0,
    amount_paid INTEGER NOT NULL DEFAULT 0,
    amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    refund_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    method TEXT,
    check_number TEXT,
    transaction_date DATE NOT NULL,
    cancellation_date DATE,
    cancelled BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_transactions_client_id ON transactions(client_id);
CREATE INDEX IF NOT EXISTS idx_transactions_lot_id    ON transactions(lot_id);
