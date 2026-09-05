CREATE TABLE IF NOT EXISTS clients (
    client_id INTEGER PRIMARY KEY,
    korean_name VARCHAR(200) NOT NULL,
    english_surname VARCHAR(200) NOT NULL,
    english_given_name VARCHAR(200) NOT NULL,
    english_middle_name VARCHAR(200),
    phone1 VARCHAR(80),
    phone2 VARCHAR(80),
    street_address VARCHAR(300) NOT NULL DEFAULT '',
    city VARCHAR(120) NOT NULL DEFAULT '',
    state VARCHAR(80) NOT NULL DEFAULT '',
    zip_code VARCHAR(40) NOT NULL DEFAULT '',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_date DATE
);

CREATE TABLE IF NOT EXISTS lots (
    lot_number TEXT PRIMARY KEY,
    price NUMERIC(12, 2) NOT NULL,
    information TEXT NOT NULL DEFAULT '',
    sold BOOLEAN NOT NULL DEFAULT FALSE,
    client_id INTEGER REFERENCES clients(client_id)
);

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id UUID PRIMARY KEY,
    client_id INTEGER NOT NULL REFERENCES clients(client_id),
    transaction_type TEXT NOT NULL,
    lot_numbers TEXT[] NOT NULL DEFAULT '{}',
    total_price NUMERIC(12, 2) NOT NULL DEFAULT 0,
    amount_paid NUMERIC(12, 2) NOT NULL DEFAULT 0,
    amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    refund_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    method TEXT,
    check_number TEXT,
    transaction_date DATE NOT NULL,
    cancellation_date DATE,
    cancelled BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_transactions_client_id ON transactions(client_id);
