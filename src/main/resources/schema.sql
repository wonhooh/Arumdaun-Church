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
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_date DATE
);

CREATE TABLE IF NOT EXISTS lots (
    lot_number INTEGER PRIMARY KEY,
    client_id UUID REFERENCES clients(id),
    price NUMERIC(12, 2) NOT NULL DEFAULT 700.00,
    paid_in_full BOOLEAN NOT NULL DEFAULT FALSE,
    information TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES clients(id),
    transaction_type TEXT NOT NULL,
    lot_number INTEGER NOT NULL REFERENCES lots(lot_number),
    amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    method TEXT,
    transaction_date DATE NOT NULL,
    memo TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_lots_client_id ON lots(client_id);
CREATE INDEX IF NOT EXISTS idx_transactions_client_id ON transactions(client_id);
CREATE INDEX IF NOT EXISTS idx_transactions_lot_number ON transactions(lot_number);
