package com.arumdaun.church;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CemeteryController {
    private final PostgresRepository repository;

    public CemeteryController(PostgresRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        repository.isAvailable();
        return Map.of("status", "UP");
    }

    @GetMapping("/api/admin/lots")
    public List<Map<String, Object>> adminLots() {
        CemeterySystem system = repository.loadSystem();
        return system.getLots().values().stream()
                .map(lot -> Map.<String, Object>of(
                        "lotNumber", lot.getLotNumber(),
                        "price", lot.getPrice(),
                        "sold", lot.isSold(),
                        "information", lot.getInformation()))
                .toList();
    }

    @GetMapping("/api/admin/tables/{tableName}")
    public PostgresRepository.AdminTableData adminTable(@PathVariable String tableName) {
        return repository.getAdminTableData(tableName);
    }

    @PostMapping(path = "/api/admin/tables/{tableName}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PostgresRepository.TableUploadResult uploadAdminTable(
            @PathVariable String tableName,
            @RequestParam("file") MultipartFile file) {
        try {
            return repository.upsertAdminTableCsv(tableName, file.getBytes());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded CSV file");
        }
    }

    @PostMapping("/api/admin/lots")
    public Map<String, Object> createLot(@RequestBody LotRequest request) {
        if (request.lotNumber() == null || request.lotNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lot number is required");
        }
        if (!Double.isFinite(request.price()) || request.price() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be zero or greater");
        }
        return repository.createLot(request.lotNumber().trim(), request.price(), request.information());
    }

    @GetMapping("/api/clients")
    public List<Map<String, Object>> clients() {
        CemeterySystem system = repository.loadSystem();
        return system.getClients(false).stream()
                .map(client -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("clientId", client.getId());
                    result.put("memberId", client.getMemberId());
                    result.put("koreanName", client.getKoreanName());
                    result.put("englishName", client.getEnglishFullName());
                    return result;
                })
                .toList();
    }

    @GetMapping("/api/admin/clients")
    public List<ClientRequest> adminClients() {
        return repository.loadSystem().getClients(false).stream().map(ClientRequest::from).toList();
    }

    @PostMapping("/api/admin/clients")
    public ClientRequest createClient(@RequestBody ClientRequest request) {
        validate(request);
        return ClientRequest.from(repository.createClient(request));
    }

    @GetMapping("/api/admin/clients/{clientId}")
    public ClientRequest adminClient(@PathVariable UUID clientId) {
        Client client = repository.loadSystem().getClient(clientId);
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found");
        }
        return ClientRequest.from(client);
    }

    @PutMapping("/api/admin/clients/{clientId}")
    public ClientRequest updateClient(@PathVariable UUID clientId, @RequestBody ClientRequest request) {
        validate(request);
        return ClientRequest.from(repository.updateClient(clientId, request));
    }

    @DeleteMapping("/api/admin/clients/{clientId}")
    public Map<String, String> deleteClient(@PathVariable UUID clientId) {
        repository.softDeleteClient(clientId);
        return Map.of("status", "deleted");
    }

    private void validate(ClientRequest request) {
        if (request.koreanName() == null || request.koreanName().isBlank()
                || request.englishSurname() == null || request.englishSurname().isBlank()
                || request.englishGivenName() == null || request.englishGivenName().isBlank()
                || request.phone1() == null || request.phone1().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Korean name, English name, and primary phone are required");
        }
    }

    public record ClientRequest(
            UUID id,
            Integer memberId,
            String koreanName,
            String englishSurname,
            String englishGivenName,
            String englishMiddleName,
            String phone1,
            String phone2,
            String streetAddress,
            String city,
            String state,
            String zipCode) {
        static ClientRequest from(Client client) {
            return new ClientRequest(client.getId(), client.getMemberId(), client.getKoreanName(),
                    client.getEnglishSurname(),
                    client.getEnglishGivenName(), client.getEnglishMiddleName(), client.getPhone1(),
                    client.getPhone2(), client.getStreetAddress(), client.getCity(), client.getState(),
                    client.getZipCode());
        }
    }

    public record LotRequest(String lotNumber, double price, String information) {
    }
}
