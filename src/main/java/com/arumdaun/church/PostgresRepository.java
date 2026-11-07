package com.arumdaun.church;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class PostgresRepository {
    private static final int MAX_CSV_BYTES = 5 * 1024 * 1024;
    private static final int MAX_CSV_ROWS = 10_000;
    private static final Map<String, TableSpec> ADMIN_TABLES = Map.of(
            "clients", new TableSpec("id"),
            "lots", new TableSpec("lot_number"),
            "transactions", new TableSpec("id"));

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return true;
    }

    public AdminTableData getAdminTableData(String tableName) {
        TableSpec table = ADMIN_TABLES.get(tableName);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }
        List<AdminColumn> tableColumns = loadAdminColumns(tableName);
        List<String> columns = tableColumns.stream().map(AdminColumn::name).toList();
        String sql = "SELECT " + columns.stream().map(PostgresRepository::quoteIdentifier)
                .collect(java.util.stream.Collectors.joining(", "))
                + " FROM " + quoteIdentifier(tableName) + " ORDER BY " + quoteIdentifier(table.keyColumn());
        return jdbcTemplate.query(sql, resultSet -> {
            List<List<Object>> rows = new ArrayList<>();
            while (resultSet.next()) {
                List<Object> row = new ArrayList<>();
                for (int column = 1; column <= columns.size(); column++) {
                    row.add(normalizeTableValue(resultSet.getObject(column)));
                }
                rows.add(row);
            }
            return new AdminTableData(tableName, columns, rows);
        });
    }

    private List<AdminColumn> loadAdminColumns(String tableName) {
        if (!ADMIN_TABLES.containsKey(tableName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }
        List<AdminColumn> columns = jdbcTemplate.queryForList("""
                SELECT column_name, data_type, udt_name
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ?
                ORDER BY ordinal_position
                """, tableName).stream()
                .map(column -> new AdminColumn(
                        (String) column.get("column_name"),
                        (String) column.get("data_type"),
                        (String) column.get("udt_name")))
                .toList();
        if (columns.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Table is not present in the current database schema");
        }
        return columns;
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Transactional
    public TableUploadResult upsertAdminTableCsv(String tableName, byte[] csvBytes) {
        TableSpec table = ADMIN_TABLES.get(tableName);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }
        List<AdminColumn> tableColumns = loadAdminColumns(tableName);
        Map<String, AdminColumn> columnByName = new LinkedHashMap<>();
        tableColumns.forEach(column -> columnByName.put(column.name(), column));
        if (csvBytes == null || csvBytes.length == 0) {
            throw badCsv("Choose a non-empty CSV file");
        }
        if (csvBytes.length > MAX_CSV_BYTES) {
            throw badCsv("CSV files must be 5 MB or smaller");
        }

        List<List<String>> records;
        try {
            String csv = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(csvBytes)).toString();
            records = AdminCsvParser.parse(csv);
        } catch (CharacterCodingException exception) {
            throw badCsv("CSV file must use UTF-8 encoding");
        } catch (IllegalArgumentException exception) {
            throw badCsv(exception.getMessage());
        }
        if (records.isEmpty() || records.getFirst().isEmpty()) {
            throw badCsv("CSV must include a header row");
        }

        List<String> columns = records.getFirst().stream().map(String::trim).toList();
        if (columns.stream().anyMatch(String::isEmpty)
                || new HashSet<>(columns).size() != columns.size()) {
            throw badCsv("CSV headers must be non-empty and unique");
        }
        for (String column : columns) {
            if (!columnByName.containsKey(column)) {
                throw badCsv("Unknown column for " + tableName + ": " + column);
            }
        }
        if (!columns.contains(table.keyColumn())) {
            throw badCsv("CSV must include the " + table.keyColumn() + " column");
        }

        List<List<Object>> values = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int recordIndex = 1; recordIndex < records.size(); recordIndex++) {
            List<String> record = records.get(recordIndex);
            if (record.size() == 1 && record.getFirst().isEmpty()) {
                continue;
            }
            if (record.size() != columns.size()) {
                throw badCsv("CSV row " + (recordIndex + 1) + " has the wrong number of values");
            }

            List<Object> row = new ArrayList<>(columns.size());
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                String column = columns.get(columnIndex);
                try {
                    row.add(convertCsvValue(columnByName.get(column), record.get(columnIndex)));
                } catch (IllegalArgumentException | DateTimeException | JsonProcessingException exception) {
                    throw badCsv("Invalid value in row " + (recordIndex + 1) + ", column " + column);
                }
            }
            int keyIndex = columns.indexOf(table.keyColumn());
            Object key = row.get(keyIndex);
            if (key == null || key.toString().isBlank()) {
                throw badCsv("Missing key value in row " + (recordIndex + 1));
            }
            if (!keys.add(key.toString())) {
                throw badCsv("Duplicate " + table.keyColumn() + " value in CSV: " + key);
            }
            if (values.size() >= MAX_CSV_ROWS) {
                throw badCsv("CSV must contain no more than " + MAX_CSV_ROWS + " rows");
            }
            values.add(row);
        }
        if (values.isEmpty()) {
            throw badCsv("CSV contains no data rows");
        }

        String sql = upsertSql(tableName, table.keyColumn(), columns);
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            executeCsvUpserts(connection, sql, values);
            return null;
        });
        return new TableUploadResult(tableName, values.size());
    }

    private void executeCsvUpserts(Connection connection, String sql, List<List<Object>> rows)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (List<Object> row : rows) {
                List<Array> arrays = new ArrayList<>();
                try {
                    for (int index = 0; index < row.size(); index++) {
                        Object value = row.get(index);
                        if (value instanceof List<?> arrayValues) {
                            Array array = connection.createArrayOf("text", arrayValues.toArray());
                            arrays.add(array);
                            statement.setArray(index + 1, array);
                        } else {
                            statement.setObject(index + 1, value);
                        }
                    }
                    statement.executeUpdate();
                } finally {
                    for (Array array : arrays) {
                        array.free();
                    }
                }
            }
        }
    }

    private Object convertCsvValue(AdminColumn column, String value) throws JsonProcessingException {
        String databaseType = column.dataType();
        if (databaseType.equals("character varying") || databaseType.equals("character")
                || databaseType.equals("text") || databaseType.equals("USER-DEFINED")) {
            return value;
        }
        if (value.isEmpty()) {
            return null;
        }
        if (databaseType.equals("uuid")) {
            return UUID.fromString(value);
        }
        if (databaseType.equals("numeric") || databaseType.equals("decimal")) {
            return new BigDecimal(value);
        }
        if (databaseType.equals("smallint") || column.udtName().equals("int2")) {
            return Short.valueOf(value);
        }
        if (databaseType.equals("integer") || column.udtName().equals("int4")) {
            return Integer.valueOf(value);
        }
        if (databaseType.equals("bigint") || column.udtName().equals("int8")) {
            return Long.valueOf(value);
        }
        if (databaseType.equals("real") || column.udtName().equals("float4")) {
            return Float.valueOf(value);
        }
        if (databaseType.equals("double precision") || column.udtName().equals("float8")) {
            return Double.valueOf(value);
        }
        if (databaseType.equals("boolean")) {
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Expected true or false");
            }
            return Boolean.valueOf(value);
        }
        if (databaseType.equals("date")) {
            return LocalDate.parse(value);
        }
        if (databaseType.equals("ARRAY")) {
            String elementType = column.udtName().startsWith("_")
                    ? column.udtName().substring(1)
                    : column.udtName();
            if (!elementType.equals("text")) {
                throw new IllegalArgumentException("Unsupported array type");
            }
            return objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
        }
        if (databaseType.equals("timestamp without time zone")) {
            return java.time.LocalDateTime.parse(value);
        }
        if (databaseType.equals("timestamp with time zone")) {
            return java.time.OffsetDateTime.parse(value);
        }
        if (databaseType.equals("time without time zone")) {
            return java.time.LocalTime.parse(value);
        }
        if (databaseType.equals("time with time zone")) {
            return java.time.OffsetTime.parse(value);
        }
        if (databaseType.equals("json") || databaseType.equals("jsonb")) {
            return objectMapper.readTree(value);
        }
        throw new IllegalArgumentException("Unsupported database column type: " + databaseType);
    }

    private String upsertSql(String tableName, String keyColumn, List<String> columns) {
        String names = columns.stream().map(PostgresRepository::quoteIdentifier)
                .collect(java.util.stream.Collectors.joining(", "));
        String placeholders = String.join(", ", java.util.Collections.nCopies(columns.size(), "?"));
        List<String> updates = columns.stream()
                .filter(column -> !column.equals(keyColumn))
                .map(column -> quoteIdentifier(column) + " = EXCLUDED." + quoteIdentifier(column))
                .toList();
        String conflictAction = updates.isEmpty()
                ? "DO NOTHING"
                : "DO UPDATE SET " + String.join(", ", updates);
        return "INSERT INTO " + quoteIdentifier(tableName) + " (" + names + ") VALUES (" + placeholders + ") "
                + "ON CONFLICT (" + quoteIdentifier(keyColumn) + ") " + conflictAction;
    }

    private ResponseStatusException badCsv(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private Object normalizeTableValue(Object value) throws SQLException {
        if (value instanceof Array array) {
            try {
                return java.util.Arrays.asList((Object[]) array.getArray());
            } finally {
                array.free();
            }
        }
        if (value instanceof java.sql.Date || value instanceof java.sql.Time
                || value instanceof java.sql.Timestamp) {
            return value.toString();
        }
        return value;
    }

    public record AdminTableData(String name, List<String> columns, List<List<Object>> rows) {
    }

    public record TableUploadResult(String table, int rowsProcessed) {
    }

    private record TableSpec(String keyColumn) {
    }

    private record AdminColumn(String name, String dataType, String udtName) {
    }

    public Map<String, Object> createLot(String lotNumber, double price, String information) {
        try {
            jdbcTemplate.update("INSERT INTO lots (lot_number, price, information) VALUES (?, ?, ?)",
                    lotNumber, price, information == null ? "" : information);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A lot with this number already exists");
        }
        return Map.of("lotNumber", lotNumber, "price", price, "information", information == null ? "" : information,
                "sold", false);
    }

    public Client createClient(CemeteryController.ClientRequest request) {
        UUID id = UUID.randomUUID();
        String memberId = request.memberId() == null ? "" : String.valueOf(request.memberId());
        String streetAddress = request.streetAddress() == null ? "" : request.streetAddress();
        String middleName = request.englishMiddleName() == null ? "" : request.englishMiddleName();
        String phone2 = request.phone2() == null ? "" : request.phone2();
        String city = request.city() == null ? "" : request.city();
        String state = request.state() == null ? "" : request.state();
        String zipCode = request.zipCode() == null ? "" : request.zipCode();

        jdbcTemplate.update("""
                INSERT INTO clients (id, member_id, korean_name, surname, given_name, middle_name,
                    phone1, phone2, street_1, street_2, city, state, zip_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, memberId, request.koreanName(), request.englishSurname(),
                request.englishGivenName(), middleName, request.phone1(), phone2,
                streetAddress, "", city, state, zipCode);
        return loadSystem().getClients().get(id);
    }

    public Client updateClient(UUID clientId, CemeteryController.ClientRequest request) {
        String memberId = request.memberId() == null ? "" : String.valueOf(request.memberId());
        String streetAddress = request.streetAddress() == null ? "" : request.streetAddress();
        String middleName = request.englishMiddleName() == null ? "" : request.englishMiddleName();
        String phone2 = request.phone2() == null ? "" : request.phone2();
        String city = request.city() == null ? "" : request.city();
        String state = request.state() == null ? "" : request.state();
        String zipCode = request.zipCode() == null ? "" : request.zipCode();

        int updated = jdbcTemplate.update("""
                UPDATE clients SET member_id = ?, korean_name = ?, surname = ?, given_name = ?,
                    middle_name = ?, phone1 = ?, phone2 = ?, street_1 = ?, street_2 = ?, city = ?,
                    state = ?, zip_code = ?
                WHERE id = ?
                """, memberId, request.koreanName(), request.englishSurname(), request.englishGivenName(),
                middleName, request.phone1(), phone2, streetAddress, "", city, state, zipCode, clientId);
        if (updated == 0) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }
        return loadSystem().getClients().get(clientId);
    }

    public void softDeleteClient(UUID clientId) {
        int updated = jdbcTemplate.update("""
            UPDATE clients SET deleted = TRUE, deleted_date = CURRENT_DATE
                WHERE id = ? AND deleted_date IS NULL
                """, clientId);
        if (updated == 1) {
            return;
        }

        Boolean clientExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM clients WHERE id = ?)", Boolean.class, clientId);
        if (!Boolean.TRUE.equals(clientExists)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found");
        }
    }

    public CemeterySystem loadSystem() {
        CemeterySystem system = new CemeterySystem();
        Map<UUID, Client> clients = new LinkedHashMap<>();
        Map<String, CemeterySystem.CemeteryLot> lots = new LinkedHashMap<>();

        jdbcTemplate.query("""
                  SELECT id, member_id, korean_name, surname, given_name, middle_name,
                      phone1, phone2, street_1, street_2, city, state, zip_code, deleted, deleted_date
                FROM clients ORDER BY korean_name
                """, rs -> {
            UUID id = UUID.fromString(rs.getString("id"));
            Client client = new Client(
                    id,
                    rs.getString("korean_name"),
                    rs.getString("surname"),
                    rs.getString("given_name"),
                    rs.getString("middle_name"),
                    rs.getString("phone1"),
                    rs.getString("phone2"),
                    rs.getString("street_1"),
                    rs.getString("city"),
                    rs.getString("state"),
                    rs.getString("zip_code"));
            String memberId = rs.getString("member_id");
            if (memberId != null && !memberId.isBlank()) {
                client.setMemberId(Integer.parseInt(memberId));
            }
            if (rs.getBoolean("deleted") || rs.getDate("deleted_date") != null) {
                client.softDelete();
            }
            clients.put(client.getId(), client);
        });

        jdbcTemplate.query("""
                SELECT id, lot_number, price, information, client_id
                FROM lots ORDER BY lot_number
                    """, rs -> {
            CemeterySystem.CemeteryLot lot = new CemeterySystem.CemeteryLot(
                    rs.getString("lot_number"),
                    rs.getDouble("price"),
                    rs.getString("information"));
            String clientId = rs.getString("client_id");
            lot.setClientId(clientId == null ? null : UUID.fromString(clientId));
            lots.put(lot.getLotNumber(), lot);
        });

        List<Purchase> purchases = new ArrayList<>();
        jdbcTemplate.query("""
                SELECT id, client_id, lot_numbers, total_price, amount_paid,
                       refund_amount, transaction_date, cancellation_date, cancelled
                FROM transactions
                WHERE transaction_type = 'PURCHASE'
                ORDER BY transaction_date, id
                """, rs -> {
            UUID purchaseId = rs.getObject("id", UUID.class);
            UUID clientId = rs.getObject("client_id", UUID.class);
            List<CemeterySystem.CemeteryLot> purchaseLots = new ArrayList<>();
            String[] lotNumbers = (String[]) rs.getArray("lot_numbers").getArray();
            for (String lotNumber : lotNumbers) {
                CemeterySystem.CemeteryLot lot = lots.get(lotNumber);
                if (lot != null) {
                    purchaseLots.add(lot);
                }
            }
            Purchase purchase = new Purchase(
                    purchaseId,
                    clientId,
                    purchaseLots,
                    rs.getDouble("total_price"),
                    rs.getDouble("amount_paid"),
                    rs.getDate("transaction_date").toLocalDate());
            purchase.setCancelled(rs.getBoolean("cancelled"));
            purchase.setRefundAmount(rs.getDouble("refund_amount"));
            Date cancellationDate = rs.getDate("cancellation_date");
            if (cancellationDate != null) {
                purchase.setCancellationDate(cancellationDate.toLocalDate());
            }
            purchases.add(purchase);
        });

        List<Payment> payments = jdbcTemplate.query("""
                SELECT id, client_id, amount, method, transaction_date,
                       transaction_type, check_number, lot_numbers, refund_amount
                FROM transactions
                WHERE transaction_type IN ('PAYMENT', 'REFUND')
                ORDER BY transaction_date, id
                    """, (rs, rowNum) -> new Payment(
                rs.getObject("id", UUID.class),
                rs.getObject("client_id", UUID.class),
                "REFUND".equalsIgnoreCase(rs.getString("transaction_type"))
                        ? rs.getDouble("refund_amount")
                        : rs.getDouble("amount"),
                rs.getString("method"),
                rs.getDate("transaction_date").toLocalDate(),
                rs.getString("transaction_type"),
                rs.getString("check_number"),
                String.join(", ", (String[]) rs.getArray("lot_numbers").getArray())));

        system.setClients(clients);
        system.setLots(lots);
        system.setPurchases(purchases);
        system.setPayments(payments);
        system.reconcileLotOwnership();
        return system;
    }
}
