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
```

The database schema is initialized from `src/main/resources/schema.sql` when the application starts.
The application reads clients, cemetery lots, purchases, and payments from PostgreSQL.
