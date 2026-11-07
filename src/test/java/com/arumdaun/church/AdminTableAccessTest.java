package com.arumdaun.church;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void databaseTableViewsRequireAdminAndReturnColumnarData() throws Exception {
        when(repository.getAdminTableData("transactions")).thenReturn(
                new PostgresRepository.AdminTableData(
                        "transactions",
                        List.of("id", "amount", "lot_numbers"),
                        List.of(List.of("transaction-1", 125.0, "[A-1]"))));

        mockMvc.perform(get("/api/admin/tables/transactions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/tables/transactions").header("Authorization", ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("transactions"))
                .andExpect(jsonPath("$.columns[0]").value("id"))
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
