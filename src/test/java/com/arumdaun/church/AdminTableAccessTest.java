package com.arumdaun.church;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CemeteryController.class)
@Import(SecurityConfig.class)
class AdminTableAccessTest {
    private static final String ADMIN_AUTHORIZATION = "Basic " + Base64.getEncoder().encodeToString(
            "admin:change-me".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostgresRepository repository;

    @Test
    void clientManagementPageRequiresAdmin() throws Exception {
        mockMvc.perform(get("/clients-admin.html"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lotTableRequiresAdminAndReturnsCurrentLotsToAdmin() throws Exception {
        CemeterySystem system = new CemeterySystem();
        system.addLot("A-1", 2500, "Corner");
        when(repository.loadSystem()).thenReturn(system);

        mockMvc.perform(get("/api/admin/lots"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/lots").header("Authorization", ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lotNumber").value("A-1"))
                .andExpect(jsonPath("$[0].price").value(2500.0))
                .andExpect(jsonPath("$[0].information").value("Corner"));
    }

    @Test
    void lotViewEditRequiresAdminAndValidatesPrice() throws Exception {
        when(repository.updateLot(7, new BigDecimal("725.50"), "Corner lot", true))
                .thenReturn(java.util.Map.of(
                        "lotNumber", 7,
                        "price", new BigDecimal("725.50"),
                        "information", "Corner lot",
                        "paidInFull", true));
        String request = """
                {"price":725.50,"information":"Corner lot","paidInFull":true}
                """;

        mockMvc.perform(put("/api/admin/lots/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/admin/lots/7")
                        .header("Authorization", ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotNumber").value(7))
                .andExpect(jsonPath("$.price").value(725.50))
                .andExpect(jsonPath("$.information").value("Corner lot"))
                .andExpect(jsonPath("$.paidInFull").value(true));

        mockMvc.perform(put("/api/admin/lots/7")
                        .header("Authorization", ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":-1,\"information\":\"Invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Price must be zero or greater"));
    }

    @Test
    void lotDeleteRequiresAdmin() throws Exception {
        mockMvc.perform(delete("/api/admin/lots/7"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/admin/lots/7")
                        .header("Authorization", ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"));

        verify(repository).deleteLot(7);
    }

    @Test
    void individualPaymentReportRequiresAdminAndReturnsLotPayments() throws Exception {
        when(repository.getIndividualPaymentReport()).thenReturn(List.of(
                new PostgresRepository.IndividualPaymentReportLot(
                        21,
                        "김민수 / Minsoo Kim",
                        new BigDecimal("900.00"),
                        List.of(new PostgresRepository.ReportTransaction(
                                "PURCHASE",
                                new BigDecimal("450.00"),
                                "Cash",
                                LocalDate.parse("2026-11-07"),
                                "")),
                        "Paid in full")));

        mockMvc.perform(get("/api/admin/reports/individual-payment-record"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/reports/individual-payment-record")
                        .header("Authorization", ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lotNumber").value(21))
                .andExpect(jsonPath("$[0].name").value("김민수 / Minsoo Kim"))
                .andExpect(jsonPath("$[0].payments[0].type").value("PURCHASE"))
                .andExpect(jsonPath("$[0].payments[0].amount").value(450.0))
                .andExpect(jsonPath("$[0].remarks").value("Paid in full"));
    }

    @Test
    void clientTableRequiresAdmin() throws Exception {
        CemeterySystem system = new CemeterySystem();
        UUID clientId = UUID.randomUUID();
        system.addClient(clientId, "김민수", "Kim", "Minsoo", "", "010-1234", null);
        UUID deletedClientId = UUID.randomUUID();
        system.addClient(deletedClientId, "박지민", "Park", "Jimin", "", "010-5678", null);
        system.softDeleteClient(deletedClientId);
        when(repository.loadSystem()).thenReturn(system);

        mockMvc.perform(get("/api/admin/clients"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/clients").header("Authorization", ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].koreanName").value("김민수"));

        mockMvc.perform(get("/api/admin/clients/" + deletedClientId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/clients/" + deletedClientId)
                        .header("Authorization", ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deletedClientId.toString()))
                .andExpect(jsonPath("$.koreanName").value("박지민"));
    }

    @Test
    void clientSaveReturnsSpecificValidationAndDatabaseReasons() throws Exception {
        String request = """
                {"koreanName":"","englishSurname":"Kim","englishGivenName":"Minsoo","phone1":"010-1234"}
                """;
        mockMvc.perform(post("/api/admin/clients")
                        .header("Authorization", ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Required fields are missing: Korean name"));

        doThrow(new DataIntegrityViolationException("database failure",
                new SQLException("duplicate key violates unique constraint clients_member_id_key")))
                .when(repository).createClient(any());
        String validRequest = """
                {"koreanName":"김민수","englishSurname":"Kim","englishGivenName":"Minsoo","phone1":"010-1234"}
                """;
        mockMvc.perform(post("/api/admin/clients")
                        .header("Authorization", ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value(
                        "Database error while saving client: duplicate key violates unique constraint clients_member_id_key"));
    }

    @Test
    void clientAndTransactionsAreSavedTogetherThroughAdminEndpoint() throws Exception {
        UUID clientId = UUID.randomUUID();
        Client savedClient = new Client(clientId, "김민수", "Kim", "Minsoo", "", "010-1234",
                "", "", "", "", "");
        String request = """
                {
                  "client": {
                    "koreanName": "김민수",
                    "englishSurname": "Kim",
                    "englishGivenName": "Minsoo",
                    "phone1": "010-1234"
                  },
                  "transactions": [{
                    "transactionType": "PURCHASE",
                    "lotNumber": 1,
                    "amount": 100,
                    "transactionDate": "2026-11-07"
                  }]
                }
                """;
        when(repository.createClientWithTransactions(any(), any())).thenReturn(savedClient);

        mockMvc.perform(post("/api/admin/clients/with-transactions")
                        .header("Authorization", ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId.toString()))
                .andExpect(jsonPath("$.koreanName").value("김민수"));

        verify(repository).createClientWithTransactions(any(), any());
    }

    @Test
    void clientEditCanAppendTransactionsAndRequiresAdmin() throws Exception {
        UUID clientId = UUID.randomUUID();
        Client savedClient = new Client(clientId, "김민수", "Kim", "Minsoo", "", "010-1234",
                "", "", "", "", "");
        String request = """
                {
                  "client": {
                    "koreanName": "김민수",
                    "englishSurname": "Kim",
                    "englishGivenName": "Minsoo",
                    "phone1": "010-1234"
                  },
                  "transactions": [{
                    "transactionType": "PAYMENT",
                    "lotNumber": 1,
                    "amount": 100.5,
                    "transactionDate": "2026-11-07",
                    "method": "Cash"
                  }]
                }
                """;
        when(repository.updateClientWithTransactions(eq(clientId), any(), anyList())).thenReturn(savedClient);

        mockMvc.perform(put("/api/admin/clients/" + clientId + "/with-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/admin/clients/" + clientId + "/with-transactions")
                        .header("Authorization", ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId.toString()));

        verify(repository).updateClientWithTransactions(eq(clientId), any(), anyList());
    }

    @Test
    void purchaseTransactionRequiresExactlyOneLotNumber() throws Exception {
        String request = """
                {
                  "client": {
                    "koreanName": "김민수",
                    "englishSurname": "Kim",
                    "englishGivenName": "Minsoo",
                    "phone1": "010-1234"
                  },
                  "transactions": [{
                    "transactionType": "PURCHASE",
                    "transactionDate": "2026-11-07",
                    "amount": 0
                  }]
                }
                """;

        mockMvc.perform(post("/api/admin/clients/with-transactions")
                        .header("Authorization", ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Transaction 1: enter one lot number"));
    }

    @Test
    void cancelTransactionTypeCanBeSavedWithoutAmount() throws Exception {
        UUID clientId = UUID.randomUUID();
        Client savedClient = new Client(clientId, "김민수", "Kim", "Minsoo", "", "010-1234",
                "", "", "", "", "");
        when(repository.createClientWithTransactions(any(), any())).thenReturn(savedClient);
        String request = """
                {
                  "client": {
                    "koreanName": "김민수",
                    "englishSurname": "Kim",
                    "englishGivenName": "Minsoo",
                    "phone1": "010-1234"
                  },
                  "transactions": [{
                    "transactionType": "CANCEL",
                    "lotNumber": 1,
                    "amount": 0,
                    "transactionDate": "2026-11-07"
                  }]
                }
                """;

        mockMvc.perform(post("/api/admin/clients/with-transactions")
                        .header("Authorization", ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId.toString()));
    }

    @Test
    void clientTransactionSaveReturnsCommitFailureReason() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(repository.updateClientWithTransactions(eq(clientId), any(), anyList()))
                .thenThrow(new TransactionSystemException("commit failed",
                        new SQLException("transaction constraint violation")));
        String request = """
                {
                  "client": {
                    "koreanName": "김민수",
                    "englishSurname": "Kim",
                    "englishGivenName": "Minsoo",
                    "phone1": "010-1234"
                  },
                  "transactions": []
                }
                """;

        mockMvc.perform(put("/api/admin/clients/" + clientId + "/with-transactions")
                        .header("Authorization", ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value(
                        "Database transaction failed while saving client: transaction constraint violation"));
    }

    @Test
    void clientTransactionHistoryRequiresAdmin() throws Exception {
        UUID clientId = UUID.randomUUID();
        when(repository.getClientTransactions(clientId)).thenReturn(List.of(
                java.util.Map.of(
                        "id", UUID.randomUUID(),
                        "transactionType", "PURCHASE",
                        "lotNumber", 1,
                        "amount", 200,
                        "transactionDate", "2026-11-07",
                        "method", "Cash")));

        mockMvc.perform(get("/api/admin/clients/" + clientId + "/transactions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/clients/" + clientId + "/transactions")
                        .header("Authorization", ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionType").value("PURCHASE"))
                .andExpect(jsonPath("$[0].lotNumber").value(1));
    }

    @Test
    void removingClientTransactionRequiresAdminAndDeletesOwnedTransaction() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/clients/" + clientId + "/transactions/" + transactionId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/admin/clients/" + clientId + "/transactions/" + transactionId)
                        .header("Authorization", ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"));

        verify(repository).deleteClientTransaction(clientId, transactionId);
    }

    @Test
    void transactionViewEditRequiresAdminAndUpdatesTransaction() throws Exception {
        UUID transactionId = UUID.randomUUID();
        CemeteryController.TransactionRequest request = new CemeteryController.TransactionRequest(
                null, "PAYMENT", 7, new BigDecimal("125.00"), LocalDate.parse("2026-11-07"), "Cash",
                "Paid by family");
        when(repository.updateAdminTransaction(transactionId, request)).thenReturn(java.util.Map.of(
                "id", transactionId,
                "clientId", UUID.randomUUID(),
                "transactionType", "PAYMENT",
                "lotNumber", 7,
                "amount", new BigDecimal("125.00"),
                "method", "Cash",
                "transactionDate", "2026-11-07",
                "memo", "Paid by family"));
        String content = """
                {"transactionType":"PAYMENT","lotNumber":7,"amount":125.00,
                 "transactionDate":"2026-11-07","method":"Cash","memo":"Paid by family"}
                """;

        mockMvc.perform(put("/api/admin/transactions/" + transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/admin/transactions/" + transactionId)
                        .header("Authorization", ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionType").value("PAYMENT"))
                .andExpect(jsonPath("$.lotNumber").value(7))
                .andExpect(jsonPath("$.amount").value(125.00))
                .andExpect(jsonPath("$.memo").value("Paid by family"));

        verify(repository).updateAdminTransaction(transactionId, request);
    }

    @Test
    void databaseTableViewsRequireAdminAndReturnColumnarData() throws Exception {
        when(repository.getAdminTableData("transactions")).thenReturn(
                new PostgresRepository.AdminTableData(
                        "transactions",
                        List.of("id", "amount", "lot_number"),
                        List.of(List.of("transaction-1", 125.0, "[A-1]"))));

        mockMvc.perform(get("/api/admin/tables/transactions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/tables/transactions").header("Authorization", ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("transactions"))
                .andExpect(jsonPath("$.columns[2]").value("lot_number"))
                .andExpect(jsonPath("$.rows[0][1]").value(125.0));
    }

    @Test
    void csvUploadRequiresAdminAndReturnsImportedRowCount() throws Exception {
        byte[] csv = "id,korean_name\n00000000-0000-0000-0000-000000000001,김민수\n"
                .getBytes(StandardCharsets.UTF_8);
        when(repository.upsertAdminTableCsv("clients", csv))
                .thenReturn(new PostgresRepository.TableUploadResult("clients", 1));
        MockMultipartFile file = new MockMultipartFile("file", "clients.csv", "text/csv", csv);

        mockMvc.perform(multipart("/api/admin/tables/clients").file(file))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart("/api/admin/tables/clients")
                        .file(new MockMultipartFile("file", "clients.csv", "text/csv", csv))
                        .header("Authorization", ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.table").value("clients"))
                .andExpect(jsonPath("$.rowsProcessed").value(1));
    }

    @Test
    void csvUploadReturnsValidationReason() throws Exception {
        byte[] csv = "id,korean_name\n00000000-0000-0000-0000-000000000001,김민수\n"
                .getBytes(StandardCharsets.UTF_8);
        when(repository.upsertAdminTableCsv("clients", csv))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown column: legacy_field"));

        mockMvc.perform(multipart("/api/admin/tables/clients")
                        .file(new MockMultipartFile("file", "clients.csv", "text/csv", csv))
                        .header("Authorization", ADMIN_AUTHORIZATION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown column: legacy_field"));
    }
}
