# Arumdaun-Church

Java Swing application for managing Arumdaun Church cemetery lots, clients, purchases, payments, cancellations, refunds, and reports.

## Run Instructions

### macOS / Linux

```bash
cd /Users/wonhooh/GitHub/Arumdaun-Church
chmod +x run.sh
./run.sh
```

### Windows

```bat
cd C:\path\to\Arumdaun-Church
run.bat
```

The application requires a Java Development Kit (JDK) and opens as a desktop window.

You can also double-click **Arumdaun Church Cemetery.app** on the macOS Desktop to launch the application.

## Data Persistence

Client data and cemetery transaction data are saved separately:

```text
data/client-data.ser
data/cemetery-records.ser
```

The two files are linked by the integer client ID. They are created on the first run and updated after successful client or transaction changes. The application loads them on later runs instead of recreating the demo data. Existing data in the former `data/cemetery-data.ser` file is migrated automatically.

Clients can be soft-deleted from the Clients tab. Deleted accounts remain stored with their historical transactions and can be displayed again using **Show Deleted Clients**.
