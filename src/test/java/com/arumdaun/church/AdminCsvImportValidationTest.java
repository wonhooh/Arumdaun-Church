package com.arumdaun.church;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AdminCsvImportValidationTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PostgresRepository repository = new PostgresRepository(jdbcTemplate, new ObjectMapper());

    @Test
    void displaysAllClientColumnsDiscoveredFromDatabaseMetadata() throws Exception {
        stubColumns("clients", columns(
                "id", "uuid",
                "member_id", "character varying",
                "korean_name", "character varying",
                "emergency_contact", "text"));
        ResultSet resultSet = mock(ResultSet.class);
        UUID clientId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1)).thenReturn(clientId);
        when(resultSet.getObject(2)).thenReturn("M-1");
        when(resultSet.getObject(3)).thenReturn("김민수");
        when(resultSet.getObject(4)).thenReturn("Family");
        doAnswer(invocation -> {
            ResultSetExtractor<?> extractor = invocation.getArgument(1);
            return extractor.extractData(resultSet);
        }).when(jdbcTemplate).query(anyString(), any(ResultSetExtractor.class));

        PostgresRepository.AdminTableData data = repository.getAdminTableData("clients");

        assertEquals(List.of("id", "member_id", "korean_name", "emergency_contact"), data.columns());
        assertEquals(List.of(clientId, "M-1", "김민수", "Family"), data.rows().getFirst());
    }

    @Test
    void rejectsUnknownTableAndColumnsBeforeDatabaseWrite() {
        ResponseStatusException unknownTable = assertThrows(ResponseStatusException.class,
                () -> repository.upsertAdminTableCsv("users", csv("id\nvalue\n")));
        stubColumns("clients", columns(
                "id", "uuid",
                "korean_name", "character varying",
                "custom_field", "text"));
        ResponseStatusException unknownColumn = assertThrows(ResponseStatusException.class,
                () -> repository.upsertAdminTableCsv("clients", csv("id,password\nvalue,secret\n")));

        assertEquals(404, unknownTable.getStatusCode().value());
        assertEquals(400, unknownColumn.getStatusCode().value());
        verify(jdbcTemplate).queryForList(anyString(), any(Object[].class));
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void requiresTableKeyAndRejectsDuplicateKeyRows() {
        stubColumns("lots", columns("lot_number", "text", "price", "numeric"));
        ResponseStatusException missingKey = assertThrows(ResponseStatusException.class,
                () -> repository.upsertAdminTableCsv("lots", csv("price\n25\n")));
        ResponseStatusException duplicateKey = assertThrows(ResponseStatusException.class,
                () -> repository.upsertAdminTableCsv("lots", csv("lot_number\nA-1\nA-1\n")));

        assertEquals(400, missingKey.getStatusCode().value());
        assertEquals(400, duplicateKey.getStatusCode().value());
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).queryForList(anyString(), any(Object[].class));
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void upsertsValidRowsUsingOnlyValidatedTableAndColumnNames() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        stubColumns("lots", columns("lot_number", "text", "price", "numeric", "information", "text"));
        doAnswer(invocation -> {
            ConnectionCallback<?> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        }).when(jdbcTemplate).execute(any(ConnectionCallback.class));

        PostgresRepository.TableUploadResult result = repository.upsertAdminTableCsv(
                "lots", csv("lot_number,price,information\nA-1,12.50,\"Corner, upper\"\n"));

        assertEquals(new PostgresRepository.TableUploadResult("lots", 1), result);
        verify(connection).prepareStatement("INSERT INTO \"lots\" (\"lot_number\", \"price\", \"information\") "
                + "VALUES (?, ?, ?) ON CONFLICT (\"lot_number\") "
                + "DO UPDATE SET \"price\" = EXCLUDED.\"price\", \"information\" = EXCLUDED.\"information\"");
        verify(statement).setObject(1, "A-1");
        verify(statement).setObject(2, new BigDecimal("12.50"));
        verify(statement).setObject(3, "Corner, upper");
        verify(statement).executeUpdate();
    }

    @Test
    void acceptsAndImportsClientFieldsDiscoveredFromDatabaseMetadata() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        stubColumns("clients", columns(
                "id", "uuid",
                "korean_name", "character varying",
                "phone1", "character varying",
                "emergency_contact", "text"));
        doAnswer(invocation -> {
            ConnectionCallback<?> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        }).when(jdbcTemplate).execute(any(ConnectionCallback.class));

        PostgresRepository.TableUploadResult result = repository.upsertAdminTableCsv(
                "clients", csv("id,korean_name,phone1,emergency_contact\n"
                        + "00000000-0000-0000-0000-000000000001,김민수,555-0100,Family\n"));

        assertEquals(1, result.rowsProcessed());
        verify(connection).prepareStatement("INSERT INTO \"clients\" "
                + "(\"id\", \"korean_name\", \"phone1\", \"emergency_contact\") VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (\"id\") DO UPDATE SET \"korean_name\" = EXCLUDED.\"korean_name\", "
                + "\"phone1\" = EXCLUDED.\"phone1\", "
                + "\"emergency_contact\" = EXCLUDED.\"emergency_contact\"");
        verify(statement).setObject(4, "Family");
        verify(statement).executeUpdate();
    }

    private void stubColumns(String tableName, Map<String, String> columns) {
        List<Map<String, Object>> metadata = columns.entrySet().stream()
                .map(column -> Map.<String, Object>of(
                        "column_name", column.getKey(),
                        "data_type", column.getValue(),
                        "udt_name", column.getValue()))
                .toList();
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(metadata);
    }

    private Map<String, String> columns(String... nameAndType) {
        Map<String, String> columns = new LinkedHashMap<>();
        for (int index = 0; index < nameAndType.length; index += 2) {
            columns.put(nameAndType[index], nameAndType[index + 1]);
        }
        return columns;
    }

    private byte[] csv(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
