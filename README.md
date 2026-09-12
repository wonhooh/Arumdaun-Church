# Arumdaun Cemetery

Spring Boot web application for managing Arumdaun Church cemetery lots, clients, purchases, payments, cancellations, and refunds.

## Run Instructions

### macOS / Linux

```bash
cd /Users/wonhooh/GitHub/ArumdaunCemetery
chmod +x run.sh
./run.sh
```

### Windows

```bat
cd C:\path\to\ArumdaunCemetery
run.bat
```

The application requires a Java Development Kit (JDK) 25 or newer and Maven. Start the Spring web application at `http://localhost:8080` with the run script.

## Data Persistence

The web application uses PostgreSQL. By default it connects to the `cemetery` database on
`localhost:5432` as the local `wonhooh` PostgreSQL user. Override the connection with:

```bash
export CEMETERY_DB_URL=jdbc:postgresql://localhost:5432/cemetery
export CEMETERY_DB_USER=wonhooh
export CEMETERY_DB_PASSWORD=your-password
export CEMETERY_ADMIN_USERNAME=admin
export CEMETERY_ADMIN_PASSWORD=your-password
```

The database schema is managed by Flyway migrations in `src/main/resources/db/migration`.
The application reads clients, cemetery lots, purchases, and payments from PostgreSQL.

## Production Database Changes

Do not edit an applied migration or use the old `schema.sql` file for production changes. Add a
new migration with the next version number, for example `V2__add_client_email.sql`, then deploy
the application. Flyway applies each new migration once and records it in `flyway_schema_history`.

Examples:

```sql
-- V2__add_client_email.sql
ALTER TABLE clients ADD COLUMN email VARCHAR(320);

-- V3__create_notes.sql
CREATE TABLE client_notes (
	note_id BIGSERIAL PRIMARY KEY,
	client_id INTEGER NOT NULL REFERENCES clients(client_id),
	note_text TEXT NOT NULL,
	created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- V4__change_phone_type.sql
ALTER TABLE clients ALTER COLUMN phone1 TYPE VARCHAR(40);
```

For removing a column, first deploy code that no longer reads or writes it, then add a later
migration with `ALTER TABLE ... DROP COLUMN`. Back up production before destructive changes and
test every migration against a copy of the production database. Existing databases are baselined
automatically at version 1; new databases run the initial `V1__initial_schema.sql` migration.

The landing page displays available lots without prices and labels reserved lots as `Reserved`.
Use the `View clients` button to open the admin client page. The client table and its create/update
APIs require HTTP Basic authentication with the admin credentials above. The default username is
`admin` and the default password is `change-me`; set both environment variables before deployment.
