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

The database schema is defined in `src/main/resources/schema.sql`. Spring runs this idempotent
script at startup for both new and existing databases. It creates missing tables and indexes without
dropping existing data. Keep schema creation and defaults in this single file; update it whenever
the application schema changes. The application reads clients, cemetery lots, purchases, and
payments from PostgreSQL.

## Production Database Changes

When changing the database schema, update `src/main/resources/schema.sql` and ensure its
`CREATE TABLE IF NOT EXISTS` and `CREATE INDEX IF NOT EXISTS` statements remain safe to run on
every startup. This file defines creation for new databases; it does not transform an older,
incompatible schema. Back up existing databases before making structural changes and apply any
required one-time data conversion separately.

The landing page displays available lots without prices and labels reserved lots as `Reserved`.
Use the `View clients` button to open the admin client page. The client table and its create/update
APIs require HTTP Basic authentication with the admin credentials above. The default username is
`admin` and the default password is `change-me`; set both environment variables before deployment.
