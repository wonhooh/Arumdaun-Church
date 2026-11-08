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
            "clients", new TableSpec("id", List.of("id")),
            "lots", new TableSpec("lot_number", List.of("lot_number")),
            "transactions", new TableSpec("id", List.of("lot_number", "transaction_date")));

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
        String orderBy = table.sortColumns().stream().map(PostgresRepository::quoteIdentifier)
            .collect(java.util.stream.Collectors.joining(", "));
        String sql = "SELECT " + columns.stream().map(PostgresRepository::quoteIdentifier)
                .collect(java.util.stream.Collectors.joining(", "))
            + " FROM " + quoteIdentifier(tableName) + " ORDER BY " + orderBy;
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

    private record TableSpec(String keyColumn, List<String> sortColumns) {
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

    public Map<String, Object> updateLot(
            int lotNumber,
            BigDecimal price,
            String information,
            Boolean paidInFull) {
        int updated = jdbcTemplate.update(
                "UPDATE lots SET price = ?, information = ?, paid_in_full = COALESCE(?, paid_in_full) WHERE lot_number = ?",
                price, information == null ? "" : information, paidInFull, lotNumber);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lot not found");
        }
        Boolean currentPaidInFull = jdbcTemplate.queryForObject(
                "SELECT paid_in_full FROM lots WHERE lot_number = ?", Boolean.class, lotNumber);
        return Map.of(
                "lotNumber", lotNumber,
                "price", price,
                "information", information == null ? "" : information,
                "paidInFull", currentPaidInFull);
    }

    @Transactional
    public void deleteLot(int lotNumber) {
        Boolean lotExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM lots WHERE lot_number = ? FOR UPDATE)",
                Boolean.class, lotNumber);
        if (!Boolean.TRUE.equals(lotExists)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lot not found");
        }
        Boolean hasTransactions = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM transactions WHERE lot_number = ?)",
                Boolean.class, lotNumber);
        if (Boolean.TRUE.equals(hasTransactions)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Lot cannot be deleted while transactions reference it");
        }
        jdbcTemplate.update("DELETE FROM lots WHERE lot_number = ?", lotNumber);
    }

    public List<Map<String, Object>> getAvailableLots(UUID clientId) {
        return jdbcTemplate.query("""
                SELECT lot_number, price
                FROM lots l
                WHERE (l.client_id IS NULL OR l.client_id = ?)
                ORDER BY lot_number
                """, (rs, rowNum) -> Map.of(
                "lotNumber", rs.getInt("lot_number"),
                "price", rs.getBigDecimal("price")), clientId);
    }

    public List<IndividualPaymentReportLot> getIndividualPaymentReport() {
        List<ReportLot> lots = jdbcTemplate.query("""
                SELECT l.lot_number, l.price, l.paid_in_full,
                       c.korean_name, c.surname, c.given_name, c.middle_name
                FROM lots l
                JOIN clients c ON c.id = l.client_id
                ORDER BY l.lot_number
                """, (rs, rowNum) -> {
            String koreanName = rs.getString("korean_name");
            String englishName = java.util.stream.Stream.of(
                    rs.getString("given_name"), rs.getString("middle_name"), rs.getString("surname"))
                    .filter(name -> name != null && !name.isBlank())
                    .collect(java.util.stream.Collectors.joining(" "));
            String name = java.util.stream.Stream.of(koreanName, englishName)
                    .filter(value -> value != null && !value.isBlank())
                    .collect(java.util.stream.Collectors.joining(" / "));
            return new ReportLot(
                    rs.getInt("lot_number"),
                    rs.getBigDecimal("price"),
                    rs.getBoolean("paid_in_full"),
                    name);
        });

        Map<Integer, List<ReportTransaction>> transactionsByLot = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT lot_number, transaction_type, amount, method, transaction_date, memo
                FROM transactions
                ORDER BY lot_number, transaction_date, id
                """, rs -> {
            int lotNumber = rs.getInt("lot_number");
            transactionsByLot.computeIfAbsent(lotNumber, ignored -> new ArrayList<>()).add(
                    new ReportTransaction(
                            rs.getString("transaction_type"),
                            rs.getBigDecimal("amount"),
                            rs.getString("method"),
                            rs.getDate("transaction_date").toLocalDate(),
                            rs.getString("memo")));
        });

        return lots.stream().map(lot -> {
            List<ReportTransaction> payments = new ArrayList<>();
            List<String> remarks = new ArrayList<>();
            for (ReportTransaction transaction : transactionsByLot.getOrDefault(lot.lotNumber(), List.of())) {
                String type = transaction.type();
                if ("PURCHASE".equals(type) || "PAYMENT".equals(type)) {
                    if (payments.size() < 4) {
                        payments.add(transaction);
                        if (transaction.memo() != null && !transaction.memo().isBlank()) {
                            remarks.add(transaction.memo().trim());
                        }
                    } else {
                        remarks.add(formatReportTransaction("Additional " + type, transaction));
                    }
                } else {
                    remarks.add(formatReportTransaction(type, transaction));
                }
            }
            if (lot.paidInFull()) {
                remarks.add("Paid in full");
            }
            return new IndividualPaymentReportLot(
                    lot.lotNumber(), lot.name(), lot.price(), payments, String.join("; ", remarks));
        }).toList();
    }

    private static String formatReportTransaction(String type, ReportTransaction transaction) {
        String amount = transaction.amount() == null ? "" : " " + DisplayFormats.formatMoney(transaction.amount());
        String method = transaction.method() == null || transaction.method().isBlank()
                ? "" : " (" + transaction.method().trim() + ")";
        String memo = transaction.memo() == null || transaction.memo().isBlank()
                ? "" : " - " + transaction.memo().trim();
        return type + ": " + DisplayFormats.formatDate(transaction.date()) + amount + method + memo;
    }

    public record IndividualPaymentReportLot(
            int lotNumber,
            String name,
            BigDecimal price,
            List<ReportTransaction> payments,
            String remarks) {
    }

    public record ReportTransaction(
            String type,
            BigDecimal amount,
            String method,
            LocalDate date,
            String memo) {
    }

    private record ReportLot(int lotNumber, BigDecimal price, boolean paidInFull, String name) {
    }

    public List<Map<String, Object>> getClientTransactions(UUID clientId) {
        return jdbcTemplate.query("""
                SELECT t.id, t.transaction_type, t.lot_number, t.amount, t.method, t.transaction_date, t.memo
                FROM transactions t
                JOIN lots l ON l.lot_number = t.lot_number
                WHERE t.client_id = ?
                ORDER BY t.transaction_date, t.id
                """, (rs, rowNum) -> {
            String type = rs.getString("transaction_type");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getObject("id", UUID.class));
            row.put("transactionType", type);
            row.put("lotNumber", rs.getInt("lot_number"));
            row.put("amount", rs.getBigDecimal("amount"));
            row.put("transactionDate", rs.getDate("transaction_date").toLocalDate());
            row.put("method", rs.getString("method") == null ? "" : rs.getString("method"));
            row.put("memo", rs.getString("memo") == null ? "" : rs.getString("memo"));
            return row;
        }, clientId);
    }

    @Transactional
    public void deleteClientTransaction(UUID clientId, UUID transactionId) {
        Integer lotNumber = jdbcTemplate.query("""
                SELECT lot_number
                FROM transactions
                WHERE id = ? AND client_id = ?
                FOR UPDATE
                """, rs -> rs.next() ? rs.getInt("lot_number") : null, transactionId, clientId);
        if (lotNumber == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Transaction not found for this client");
        }

        int deleted = jdbcTemplate.update(
                "DELETE FROM transactions WHERE id = ? AND client_id = ?", transactionId, clientId);
        if (deleted != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transaction changed before it could be removed; reload and try again");
        }
        updateLotOwnership(lotNumber);
    }

    @Transactional
    public Map<String, Object> updateAdminTransaction(
            UUID transactionId,
            CemeteryController.TransactionRequest transaction) {
        UUID clientId = jdbcTemplate.query("""
                SELECT client_id FROM transactions WHERE id = ? FOR UPDATE
                """, rs -> rs.next() ? rs.getObject("client_id", UUID.class) : null, transactionId);
        if (clientId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found or has no client");
        }
        CemeteryController.TransactionRequest transactionWithId = new CemeteryController.TransactionRequest(
                transactionId,
                transaction.transactionType(),
                transaction.lotNumber(),
                transaction.amount(),
                transaction.transactionDate(),
                transaction.method(),
                transaction.memo());
        saveTransactions(clientId, List.of(transactionWithId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", transactionId);
        result.put("clientId", clientId);
        result.put("transactionType", transaction.transactionType());
        result.put("lotNumber", transaction.lotNumber());
        result.put("amount", "CANCEL".equals(transaction.transactionType())
                ? BigDecimal.ZERO : transaction.amount());
        result.put("method", transaction.method());
        result.put("transactionDate", transaction.transactionDate());
        result.put("memo", transaction.memo() == null ? "" : transaction.memo());
        return result;
    }

    @Transactional
    public Client createClientWithTransactions(
            CemeteryController.ClientRequest request,
            List<CemeteryController.TransactionRequest> transactions) {
        Client client = createClient(request);
        saveTransactions(client.getId(), transactions);
        return client;
    }

    @Transactional
    public Client updateClientWithTransactions(
            UUID clientId,
            CemeteryController.ClientRequest request,
            List<CemeteryController.TransactionRequest> transactions) {
        Client client = updateClient(clientId, request);
        saveTransactions(clientId, transactions);
        return client;
    }

    private void saveTransactions(
            UUID clientId,
            List<CemeteryController.TransactionRequest> transactions) {
        Set<Integer> assignedLots = new HashSet<>();

        for (int index = 0; index < transactions.size(); index++) {
            CemeteryController.TransactionRequest transaction = transactions.get(index);
            Integer lotNumber = transaction.lotNumber();
            BigDecimal lotPrice = jdbcTemplate.query("""
                    SELECT price FROM lots WHERE lot_number = ? FOR UPDATE
                    """, rs -> rs.next() ? rs.getBigDecimal("price") : null, lotNumber);
            if (lotPrice == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Transaction " + (index + 1) + ": lot " + lotNumber + " does not exist");
            }
            UUID existingClientId = jdbcTemplate.queryForObject(
                    "SELECT client_id FROM lots WHERE lot_number = ?", UUID.class, lotNumber);
            if (transaction.id() == null) {
                if ("PURCHASE".equals(transaction.transactionType())) {
                    if (assignedLots.contains(lotNumber)
                            || (existingClientId != null && !existingClientId.equals(clientId))) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Transaction " + (index + 1) + ": lot " + lotNumber + " is already assigned");
                    }
                    assignedLots.add(lotNumber);
                } else if ("CANCEL".equals(transaction.transactionType())) {
                    requireActivePurchase(clientId, lotNumber, null, index);
                } else if (existingClientId != null && !existingClientId.equals(clientId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Transaction " + (index + 1) + ": lot " + lotNumber + " belongs to another client");
                }

                insertTransaction(clientId, transaction, lotNumber,
                        "CANCEL".equals(transaction.transactionType()) ? BigDecimal.ZERO
                                : transaction.amount() == null ? BigDecimal.ZERO : transaction.amount());
                if ("PURCHASE".equals(transaction.transactionType())) {
                    jdbcTemplate.update("UPDATE lots SET client_id = ? WHERE lot_number = ?", clientId, lotNumber);
                } else if ("CANCEL".equals(transaction.transactionType())) {
                    jdbcTemplate.update("UPDATE lots SET client_id = NULL WHERE lot_number = ?", lotNumber);
                }
                continue;
            }

            Integer previousLotNumber = jdbcTemplate.query("""
                    SELECT lot_number FROM transactions WHERE id = ? AND client_id = ? FOR UPDATE
                    """, rs -> rs.next() ? rs.getInt("lot_number") : null, transaction.id(), clientId);
            if (previousLotNumber == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transaction " + (index + 1) + " was not found for this client");
            }
            if ("CANCEL".equals(transaction.transactionType())) {
                requireActivePurchase(clientId, lotNumber, transaction.id(), index);
            } else if ("PURCHASE".equals(transaction.transactionType())
                    && existingClientId != null && !existingClientId.equals(clientId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Transaction " + (index + 1) + ": lot " + lotNumber + " belongs to another client");
            } else if (!Set.of("PURCHASE", "CANCEL").contains(transaction.transactionType())
                    && existingClientId != null && !existingClientId.equals(clientId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Transaction " + (index + 1) + ": lot " + lotNumber + " belongs to another client");
            }
            jdbcTemplate.update("""
                    UPDATE transactions
                    SET transaction_type = ?, lot_number = ?, amount = ?, method = ?, transaction_date = ?, memo = ?
                    WHERE id = ? AND client_id = ?
                    """, transaction.transactionType(), lotNumber,
                    "CANCEL".equals(transaction.transactionType()) || transaction.amount() == null
                            ? BigDecimal.ZERO : transaction.amount(),
                    transaction.method(), Date.valueOf(transaction.transactionDate()),
                    transaction.memo() == null ? "" : transaction.memo(), transaction.id(), clientId);
            updateLotOwnership(previousLotNumber);
            if (!previousLotNumber.equals(lotNumber)) {
                updateLotOwnership(lotNumber);
            }
        }
    }

    private void requireActivePurchase(UUID clientId, int lotNumber, UUID excludedTransactionId, int rowIndex) {
        Boolean hasPurchase = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM transactions purchase
                    WHERE purchase.client_id = ?
                      AND purchase.lot_number = ?
                      AND purchase.transaction_type = 'PURCHASE'
                      AND NOT EXISTS (
                          SELECT 1 FROM transactions cancellation
                          WHERE cancellation.client_id = purchase.client_id
                            AND cancellation.lot_number = purchase.lot_number
                            AND cancellation.transaction_type = 'CANCEL'
                            AND (? IS NULL OR cancellation.id <> ?)
                            AND cancellation.transaction_date >= purchase.transaction_date
                      )
                )
                """, Boolean.class, clientId, lotNumber, excludedTransactionId, excludedTransactionId);
        if (!Boolean.TRUE.equals(hasPurchase)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Transaction " + (rowIndex + 1) + ": no active purchase exists for lot " + lotNumber);
        }
    }

    private void updateLotOwnership(int lotNumber) {
        List<Map<String, Object>> latestOwnership = jdbcTemplate.query("""
                SELECT transaction_type, client_id
                FROM transactions
                WHERE lot_number = ? AND transaction_type IN ('PURCHASE', 'CANCEL')
                ORDER BY transaction_date DESC,
                         CASE WHEN transaction_type = 'CANCEL' THEN 1 ELSE 0 END DESC,
                         id DESC
                LIMIT 1
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", rs.getString("transaction_type"));
            row.put("clientId", rs.getObject("client_id", UUID.class));
            return row;
        }, lotNumber);
        UUID clientId = !latestOwnership.isEmpty() && "PURCHASE".equals(latestOwnership.getFirst().get("type"))
                ? (UUID) latestOwnership.getFirst().get("clientId")
                : null;
        jdbcTemplate.update("UPDATE lots SET client_id = ? WHERE lot_number = ?", clientId, lotNumber);
    }

    private void insertTransaction(
            UUID clientId,
            CemeteryController.TransactionRequest transaction,
            Integer lotNumber,
            BigDecimal amount) {
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    client_id, transaction_type, lot_number, amount, method, transaction_date, memo)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, clientId, transaction.transactionType(), lotNumber, amount,
                transaction.method(), Date.valueOf(transaction.transactionDate()),
                transaction.memo() == null ? "" : transaction.memo());
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
        return clientFromRequest(id, request);
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
        return clientFromRequest(clientId, request);
    }

    private Client clientFromRequest(UUID id, CemeteryController.ClientRequest request) {
        Client client = new Client(
                id,
                request.koreanName(),
                request.englishSurname(),
                request.englishGivenName(),
                request.englishMiddleName(),
                request.phone1(),
                request.phone2(),
                request.streetAddress(),
                request.city(),
                request.state(),
                request.zipCode());
        client.setMemberId(request.memberId());
        return client;
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
                SELECT lot_number, price, information, client_id
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
        Map<Integer, Purchase> purchasesByLot = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT t.id, t.client_id, t.lot_number, t.amount, t.transaction_date
                FROM transactions t
                WHERE t.transaction_type = 'PURCHASE'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM transactions cancellation
                      WHERE cancellation.client_id = t.client_id
                        AND cancellation.lot_number = t.lot_number
                        AND cancellation.transaction_type = 'CANCEL'
                        AND cancellation.transaction_date >= t.transaction_date
                  )
                ORDER BY t.transaction_date, t.id
                """, rs -> {
            UUID purchaseId = rs.getObject("id", UUID.class);
            UUID clientId = rs.getObject("client_id", UUID.class);
            int lotNumber = rs.getInt("lot_number");
            CemeterySystem.CemeteryLot lot = lots.get(String.valueOf(lotNumber));
            if (lot == null) {
                return;
            }
            Purchase purchase = new Purchase(
                    purchaseId,
                    clientId,
                    List.of(lot),
                    lot.getPrice(),
                    rs.getDouble("amount"),
                    rs.getDate("transaction_date").toLocalDate());
            purchases.add(purchase);
            purchasesByLot.put(lotNumber, purchase);
        });

        List<Payment> payments = jdbcTemplate.query("""
                SELECT id, client_id, lot_number, amount, method, transaction_date, transaction_type
                FROM transactions
                WHERE transaction_type IN ('PAYMENT', 'REFUND')
                ORDER BY transaction_date, id
                """, (rs, rowNum) -> {
            UUID clientId = rs.getObject("client_id", UUID.class);
            int lotNumber = rs.getInt("lot_number");
            String type = rs.getString("transaction_type");
            double amount = rs.getDouble("amount");
            Purchase purchase = purchasesByLot.get(lotNumber);
            if (purchase != null && clientId != null && clientId.equals(purchase.getClientId())) {
                if ("PAYMENT".equals(type)) {
                    purchase.addPayment(amount);
                } else {
                    purchase.setRefundAmount(purchase.getRefundAmount() + amount);
                    purchase.setAmountPaid(Math.max(0, purchase.getAmountPaid() - amount));
                }
            }
            return new Payment(
                    rs.getObject("id", UUID.class),
                    clientId,
                    amount,
                    rs.getString("method"),
                    rs.getDate("transaction_date").toLocalDate(),
                    type,
                    null,
                    String.valueOf(lotNumber));
        });

        system.setClients(clients);
        system.setLots(lots);
        system.setPurchases(purchases);
        system.setPayments(payments);
        return system;
    }
}
