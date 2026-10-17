-- Assign stable UUID primary keys, then preserve all client relationships while
-- converting the legacy integer foreign keys.
ALTER TABLE clients ADD COLUMN id UUID DEFAULT gen_random_uuid();
UPDATE clients SET id = gen_random_uuid() WHERE id IS NULL;
ALTER TABLE clients ALTER COLUMN id SET NOT NULL;

ALTER TABLE lots DROP CONSTRAINT lots_client_id_fkey;
ALTER TABLE transactions DROP CONSTRAINT transactions_client_id_fkey;

ALTER TABLE lots ADD COLUMN client_uuid UUID;
UPDATE lots AS lot
SET client_uuid = client.id
FROM clients AS client
WHERE lot.client_id = client.client_id;
ALTER TABLE lots DROP COLUMN client_id;
ALTER TABLE lots RENAME COLUMN client_uuid TO client_id;
ALTER TABLE lots ADD CONSTRAINT lots_client_id_fkey
    FOREIGN KEY (client_id) REFERENCES clients(id);

ALTER TABLE transactions ADD COLUMN client_uuid UUID;
UPDATE transactions AS transaction_row
SET client_uuid = client.id
FROM clients AS client
WHERE transaction_row.client_id = client.client_id;
ALTER TABLE transactions DROP COLUMN client_id;
ALTER TABLE transactions RENAME COLUMN client_uuid TO client_id;
ALTER TABLE transactions ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE transactions ADD CONSTRAINT transactions_client_id_fkey
    FOREIGN KEY (client_id) REFERENCES clients(id);

ALTER TABLE clients DROP CONSTRAINT clients_pkey;
ALTER TABLE clients DROP COLUMN client_id;
ALTER TABLE clients ADD CONSTRAINT clients_pkey PRIMARY KEY (id);

ALTER TABLE transactions RENAME COLUMN transaction_id TO id;

ALTER TABLE lots DROP CONSTRAINT lots_pkey;
ALTER TABLE lots ADD COLUMN id UUID DEFAULT gen_random_uuid();
UPDATE lots SET id = gen_random_uuid() WHERE id IS NULL;
ALTER TABLE lots ALTER COLUMN id SET NOT NULL;
ALTER TABLE lots ADD CONSTRAINT lots_pkey PRIMARY KEY (id);
ALTER TABLE lots ADD CONSTRAINT lots_lot_number_key UNIQUE (lot_number);

ALTER TABLE clients RENAME COLUMN english_surname TO surname;
ALTER TABLE clients RENAME COLUMN english_given_name TO given_name;
ALTER TABLE clients RENAME COLUMN english_middle_name TO middle_name;
ALTER TABLE clients RENAME COLUMN street_address TO street_1;
ALTER TABLE clients ADD COLUMN street_2 VARCHAR(256) NOT NULL DEFAULT '';
UPDATE clients SET member_id = '' WHERE member_id IS NULL;
ALTER TABLE clients ALTER COLUMN member_id TYPE VARCHAR(64) USING member_id::TEXT;
ALTER TABLE clients ALTER COLUMN member_id SET DEFAULT '';
ALTER TABLE clients ALTER COLUMN member_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_client_id ON transactions(client_id);