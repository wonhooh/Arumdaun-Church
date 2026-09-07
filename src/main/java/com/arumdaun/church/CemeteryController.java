package com.arumdaun.church;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
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

    @GetMapping("/api/lots")
    public List<Map<String, Object>> lots() {
        CemeterySystem system = repository.loadSystem();
        return system.getLots().values().stream()
                .map(lot -> Map.<String, Object>of(
                        "lotNumber", lot.getLotNumber(),
                        "price", lot.getPrice(),
                        "sold", lot.isSold(),
                        "information", lot.getInformation()))
                .toList();
    }

    @GetMapping("/api/clients")
    public List<Map<String, Object>> clients() {
        CemeterySystem system = repository.loadSystem();
        return system.getClients(false).stream()
                .map(client -> Map.<String, Object>of(
                        "clientId", client.getClientId(),
                        "koreanName", client.getKoreanName(),
                        "englishName", client.getEnglishFullName()))
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

    @PutMapping("/api/admin/clients/{clientId}")
    public ClientRequest updateClient(@PathVariable int clientId, @RequestBody ClientRequest request) {
        validate(request);
        return ClientRequest.from(repository.updateClient(clientId, request));
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
            Integer clientId,
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
            return new ClientRequest(client.getClientId(), client.getKoreanName(), client.getEnglishSurname(),
                    client.getEnglishGivenName(), client.getEnglishMiddleName(), client.getPhone1(),
                    client.getPhone2(), client.getStreetAddress(), client.getCity(), client.getState(),
                    client.getZipCode());
        }
    }
}
