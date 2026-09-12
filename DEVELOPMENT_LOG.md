# Arumdaun Church Cemetery Sales Management System

## Project Objective

Create a Java application that runs on both Windows and macOS to manage church cemetery lot sales.

## Maintenance Rule

This log is the living record of the project. Any new feature, fix, requirement change, or execution result should be added here so the documentation stays accurate as the project evolves.

## Interface Update

A Swing desktop user interface was added in place of the earlier simple text menu. The application now runs as a graphical Java window for easier use on both Windows and macOS.

The Spring web landing page now includes a client table with client ID, Korean name, English name,
and View / edit actions, plus an Add new client action. Admin users can manage client records from
`/admin.html`; the page uses HTTP Basic authentication and persists create/update changes to PostgreSQL.

Database schema evolution now uses Flyway migrations under `src/main/resources/db/migration`.
Existing non-empty databases are baselined at version 1 without dropping data, while new databases
run `V1__initial_schema.sql`. Future tables, columns, column type changes, and removals must be
introduced through new numbered migrations and tested against a production database backup.

Client `member_id` is now nullable and is loaded from PostgreSQL, accepted by client create/update
APIs, returned in API responses, shown in the admin client table and form, and included in client
diagnostic text. Null member IDs are displayed explicitly as `null` in the client table.

## Final Requirement Alignment

The current version reflects the most recent business rules:

- Client IDs are simple integers beginning at 100.
- Each client includes two names: Korean and English.
- Korean name is stored in a single "성명" field.
- English names are split into surname, given name, and optional middle name.
- Phone numbers must be entered in nnn-nnn-nnnn format.
- Check payments require a check number and amount.
- Purchase, payment, and cancellation dates may be left blank when unknown.
- Name and phone entry boxes are wide enough for longer values.
- Phone 1 is mandatory; Phone 2 is optional and stored as null when omitted, but must use nnn-nnn-nnnn format when entered.
- Client addresses use separate Street Address, City, State, and ZIP Code fields.
- State uses a two-letter U.S. abbreviation and ZIP uses 12345 or 12345-6789 format.
- Addresses may be blank, but partially entered addresses are rejected.
- The Clients tab lists clients by ID and name.
- Clicking a client opens a separate editable information window with names, phone numbers, and address.
- Client purchase, cancellation, refund, and payment records are shown in that client information window.
- The main Operations view lists every lot with its price, status, client ID, and Korean 성명.
- Sold lots display only the lot number and "Sold"; their price and client identity are hidden.
- The main view no longer displays the activity log; transaction history is client-specific.
- A third Payment History tab lists payment and refund records with lot ID, client ID, Korean 성명, type, method, amount, and date.
- Payment History columns are sortable by clicking their headers, including lot ID, client, type, amount, and date.
- Purchase, payment, and cancellation entry panels are hidden from the Operations screen.
- The Register Client panel is also hidden from the Operations screen; client information remains available from the Clients tab.
- A Purchase & Cancellation tab contains the purchase and cancellation controls.
- Clicking Purchase Lots opens the Register Client window before continuing with the purchase action.
- Payment entry is combined with the Purchase & Cancellation tab, outside the Operations screen.
- Purchase & Cancellation uses one pane with radio buttons for Purchase or Cancellation.
- Each transaction handles one lot at a time; client and lot choices use dropdowns.
- Purchase and cancellation now use a single shared pane with the selected client's Korean and English name displayed.
- A non-zero amount and transaction date in YYYY-MM-DD format are required from that pane.
- The shared pane accepts one Client ID in a text field, not a client dropdown, and its action button is labeled Purchase or Cancel according to the selected radio button.
- All entered amounts must begin with `$`; commas are accepted, and displayed monetary values use the `$` prefix.
- Purchase/Cancellation has one shared section with the Payment or Refund Amount directly below the radio buttons.
- Record Payment and View Payment History buttons were removed from the transaction screen; Payment History remains available as a display tab.
- In cancellation mode, the lot dropdown displays only the lot number; the purchase UUID is used internally and is not shown.
- Clicking a lot in the Lots tab opens Purchase & Cancellation with that lot selected.
- Clicking a lot in the Lot Statuses tab opens Purchase & Cancellation with that lot selected as well.
- Available lots open in Purchase mode; sold lots open in Cancel mode and populate the owner's client ID.
- Outside the client-registration form, Korean and English names are displayed together in one `Name` cell.
- The Clients tab displays each client's active lot numbers in a `Lots Owned` column immediately after `Name`.
- Payment History starts with sample payment data, including a partially paid purchase.
- Lot Statuses shows every lot with its price, sold client identity, and an outstanding balance when applicable.
- Lots lists Lot, Status, Price, and arbitrary Lot Information in that order.
- Lots does not display client ID, Korean name, or other client information.
- Price is hidden for sold lots; lot information remains visible.
- The Lots tab no longer contains the Print Reports button.
- Tab panels are separated into OperationsTab, ClientsTab, PaymentHistoryTab, PurchaseCancellationTab, and LotStatusTab Java classes.
- Cancellation awards a full refund.
- Reports are printed from the Swing UI.
- Client data is persisted separately to data/client-data.ser, while lots and cemetery transactions are persisted to data/cemetery-records.ser using Java serialization.
- The two stores are linked by integer client ID; the saved data is loaded at startup and demo records are created only when no saved data exists.
- Existing data in the former data/cemetery-data.ser file is migrated into the two separate stores automatically.
- Successful client edits, purchases, payments, and cancellations save the current system automatically.
- Clients support soft deletion: deleted accounts remain persisted and historical transactions are preserved.
- The Clients tab provides a Show Deleted Clients mechanism and marks deleted rows with Deleted status.
- Each client row has a trash-can icon that immediately hides the client from the active list, soft-deletes the record, and has a tooltip.
- The macOS Desktop contains an `Arumdaun Church Cemetery.app` launcher that compiles all Java classes and starts the Swing application.
- run.sh now compiles all separated Java source files before launching.

## Requirements Captured

1. Clients can purchase one or more cemetery lots.
2. A client can cancel a purchase and receive a full refund.
3. Payments must be tracked.
4. Reports must be printable.

## Implementation Summary

The application was created as a Java Swing desktop program for Windows and macOS.

Main features:

- register clients
- view available cemetery lots
- purchase one or more lots
- cancel a purchase with full refund
- record payment transactions
- view payment history by client
- print sales and payment reports
- track purchase/payment/cancellation dates, allowing blank values when unknown
- list clients by ID and name in a separate tab
- edit client names, phone numbers, and address in a separate detail window
- register clients with standardized U.S. address fields in the Register Client window

## Files in the Project

- [README.md](README.md)
- [src/main/java/com/arumdaun/church/Main.java](src/main/java/com/arumdaun/church/Main.java)
- [src/main/java/com/arumdaun/church/OperationsTab.java](src/main/java/com/arumdaun/church/OperationsTab.java)
- [src/main/java/com/arumdaun/church/ClientsTab.java](src/main/java/com/arumdaun/church/ClientsTab.java)
- [src/main/java/com/arumdaun/church/PaymentHistoryTab.java](src/main/java/com/arumdaun/church/PaymentHistoryTab.java)
- [src/main/java/com/arumdaun/church/PurchaseCancellationTab.java](src/main/java/com/arumdaun/church/PurchaseCancellationTab.java)
- [src/main/java/com/arumdaun/church/LotStatusTab.java](src/main/java/com/arumdaun/church/LotStatusTab.java)
- [run.sh](run.sh)
- [run.bat](run.bat)

## Execution Record

Verified compile command:

```bash
cd /Users/wonhooh/GitHub/Arumdaun-Church && mkdir -p out && javac -d out src/main/java/com/arumdaun/church/*.java
```

Result: successful compilation with no Java errors. The build completed with exit code 0.

The client directory and detail-window update was also compiled successfully with the same command.

Launch command:

```bash
cd /Users/wonhooh/GitHub/Arumdaun-Church
./run.sh
```

Windows equivalent:

```bat
cd /Users/wonhooh/GitHub/Arumdaun-Church
run.bat
```

Observed output for compile verification:

```text
wonhooh@Wonhos-Mac-mini Arumdaun-Church %
```

This confirms the current Swing project compiles successfully. The GUI is then launched by the run scripts above.

## Development Notes

- The solution uses Java Swing for portability across Windows and macOS.
- No database is required in this version; all data is kept in memory while the app is running.
- The implementation is suitable for a prototype or lightweight local management tool.
- The latest change set finalized the optional date fields and single Korean name field requested by the user.
