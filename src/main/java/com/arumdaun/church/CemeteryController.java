package com.arumdaun.church;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
                        "name", client.getDisplayName(),
                        "phone", client.getPhone1()))
                .toList();
    }
}
