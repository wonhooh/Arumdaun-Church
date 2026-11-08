package com.arumdaun.church;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
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

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@SecurityRequirement(name = "adminBasicAuth")
public class CemeteryController {
    private final PostgresRepository repository;
    private final IndividualPaymentReportPdfService reportPdfService;

    public CemeteryController(PostgresRepository repository, IndividualPaymentReportPdfService reportPdfService) {
        this.repository = repository;
        this.reportPdfService = reportPdfService;
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

    @GetMapping("/api/admin/lots/available")
    public List<Map<String, Object>> availableLots(
            @RequestParam(value = "clientId", required = false) UUID clientId) {
        return repository.getAvailableLots(clientId);
    }

    @GetMapping("/api/admin/reports/individual-payment-record")
    public List<PostgresRepository.IndividualPaymentReportLot> individualPaymentReport() {
        return repository.getIndividualPaymentReport();
    }

    @GetMapping(path = "/api/admin/reports/individual-payment-record.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> individualPaymentReportPdf() {
        try {
            byte[] pdf = reportPdfService.generate(repository.getIndividualPaymentReport());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header("Content-Disposition", ContentDisposition.inline()
                            .filename("individual-payment-report.pdf").build().toString())
                    .body(pdf);
        } catch (IOException | com.lowagie.text.DocumentException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to generate individual payment report PDF", exception);
        }
    }

    @GetMapping("/api/admin/tables/{tableName}")
    public PostgresRepository.AdminTableData adminTable(@PathVariable String tableName) {
        return repository.getAdminTableData(tableName);
    }

    @PostMapping(path = "/api/admin/tables/{tableName}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAdminTable(
            @PathVariable String tableName,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Choose a non-empty CSV file"));
        }
        try {
            return ResponseEntity.ok(repository.upsertAdminTableCsv(tableName, file.getBytes()));
        } catch (ResponseStatusException exception) {
            String reason = exception.getReason() == null ? "CSV upload was rejected" : exception.getReason();
            return ResponseEntity.status(exception.getStatusCode()).body(Map.of("detail", reason));
        } catch (IOException exception) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Unable to read uploaded CSV file"));
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

    @PutMapping("/api/admin/lots/{lotNumber}")
    public ResponseEntity<?> updateLot(
            @PathVariable int lotNumber,
            @RequestBody LotUpdateRequest request) {
        if (request == null || request.price() == null || request.price().signum() < 0) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Price must be zero or greater"));
        }
        return ResponseEntity.ok(repository.updateLot(
                lotNumber, request.price(), request.information(), request.paidInFull()));
    }

    @DeleteMapping("/api/admin/lots/{lotNumber}")
    public ResponseEntity<?> deleteLot(@PathVariable int lotNumber) {
        try {
            repository.deleteLot(lotNumber);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (ResponseStatusException exception) {
            return transactionSaveFailure(exception);
        } catch (DataAccessException exception) {
            return transactionSaveFailure(exception);
        } catch (TransactionException exception) {
            return transactionSaveFailure(exception);
        }
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
    public ResponseEntity<?> createClient(@RequestBody ClientRequest request) {
        try {
            validate(request);
            return ResponseEntity.ok(ClientRequest.from(repository.createClient(request)));
        } catch (ResponseStatusException exception) {
            return clientSaveFailure(exception);
        } catch (DataAccessException exception) {
            return clientSaveFailure(exception);
        } catch (TransactionException exception) {
            return clientSaveFailure(exception);
        }
    }

    @PostMapping("/api/admin/clients/with-transactions")
    public ResponseEntity<?> createClientWithTransactions(@RequestBody ClientWithTransactionsRequest request) {
        try {
            if (request == null || request.client() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client details are required");
            }
            validate(request.client());
            List<TransactionRequest> transactions = request.transactions() == null ? List.of() : request.transactions();
            if (transactions.size() > 50) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A maximum of 50 transactions can be added at once");
            }
            validateTransactions(transactions);
            return ResponseEntity.ok(ClientRequest.from(
                    repository.createClientWithTransactions(request.client(), transactions)));
        } catch (ResponseStatusException exception) {
            return clientSaveFailure(exception);
        } catch (DataAccessException exception) {
            return clientSaveFailure(exception);
        } catch (TransactionException exception) {
            return clientSaveFailure(exception);
        }
    }

    @GetMapping("/api/admin/clients/{clientId}/transactions")
    public List<Map<String, Object>> adminClientTransactions(@PathVariable UUID clientId) {
        return repository.getClientTransactions(clientId);
    }

    @DeleteMapping("/api/admin/clients/{clientId}/transactions/{transactionId}")
    public ResponseEntity<?> deleteClientTransaction(
            @PathVariable UUID clientId,
            @PathVariable UUID transactionId) {
        try {
            repository.deleteClientTransaction(clientId, transactionId);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (ResponseStatusException exception) {
            return clientSaveFailure(exception);
        } catch (DataAccessException exception) {
            return clientSaveFailure(exception);
        } catch (TransactionException exception) {
            return clientSaveFailure(exception);
        }
    }

    @PutMapping("/api/admin/transactions/{transactionId}")
    public ResponseEntity<?> updateAdminTransaction(
            @PathVariable UUID transactionId,
            @RequestBody TransactionRequest request) {
        try {
            if (request == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction details are required");
            }
            validateTransactions(List.of(request));
            return ResponseEntity.ok(repository.updateAdminTransaction(transactionId, request));
        } catch (ResponseStatusException exception) {
            return transactionSaveFailure(exception);
        } catch (DataAccessException exception) {
            return transactionSaveFailure(exception);
        } catch (TransactionException exception) {
            return transactionSaveFailure(exception);
        }
    }

    @PutMapping("/api/admin/clients/{clientId}/with-transactions")
    public ResponseEntity<?> updateClientWithTransactions(
            @PathVariable UUID clientId,
            @RequestBody ClientWithTransactionsRequest request) {
        try {
            if (request == null || request.client() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client details are required");
            }
            validate(request.client());
            List<TransactionRequest> transactions = request.transactions() == null ? List.of() : request.transactions();
            if (transactions.size() > 50) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A maximum of 50 transactions can be added at once");
            }
            validateTransactions(transactions);
            return ResponseEntity.ok(ClientRequest.from(
                    repository.updateClientWithTransactions(clientId, request.client(), transactions)));
        } catch (ResponseStatusException exception) {
            return clientSaveFailure(exception);
        } catch (DataAccessException exception) {
            return clientSaveFailure(exception);
        } catch (TransactionException exception) {
            return clientSaveFailure(exception);
        }
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
    public ResponseEntity<?> updateClient(@PathVariable UUID clientId, @RequestBody ClientRequest request) {
        try {
            validate(request);
            return ResponseEntity.ok(ClientRequest.from(repository.updateClient(clientId, request)));
        } catch (ResponseStatusException exception) {
            return clientSaveFailure(exception);
        } catch (DataAccessException exception) {
            return clientSaveFailure(exception);
        }
    }

    @DeleteMapping("/api/admin/clients/{clientId}")
    public Map<String, String> deleteClient(@PathVariable UUID clientId) {
        repository.softDeleteClient(clientId);
        return Map.of("status", "deleted");
    }

    private void validate(ClientRequest request) {
        List<String> missingFields = new ArrayList<>();
        if (request.koreanName() == null || request.koreanName().isBlank()) {
            missingFields.add("Korean name");
        }
        if (request.englishSurname() == null || request.englishSurname().isBlank()) {
            missingFields.add("English surname");
        }
        if (request.englishGivenName() == null || request.englishGivenName().isBlank()) {
            missingFields.add("English given name");
        }
        if (request.phone1() == null || request.phone1().isBlank()) {
            missingFields.add("Phone 1");
        }
        if (!missingFields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Required fields are missing: " + String.join(", ", missingFields));
        }
    }

    private void validateTransactions(List<TransactionRequest> transactions) {
        Set<String> validTypes = Set.of("PURCHASE", "PAYMENT", "REFUND", "CANCEL");
        for (int index = 0; index < transactions.size(); index++) {
            TransactionRequest transaction = transactions.get(index);
            String prefix = "Transaction " + (index + 1) + ": ";
            if (transaction == null || transaction.transactionType() == null
                    || !validTypes.contains(transaction.transactionType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        prefix + "choose Purchase, Payment, or Refund");
            }
            if (transaction.transactionDate() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "date is required");
            }
            BigDecimal amount = transaction.amount() == null ? BigDecimal.ZERO : transaction.amount();
            if (amount.signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "amount cannot be negative");
            }
            if (Set.of("PAYMENT", "REFUND").contains(transaction.transactionType()) && amount.signum() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "amount must be greater than zero");
            }
                if (transaction.lotNumber() != null
                    && (transaction.lotNumber() < 1 || transaction.lotNumber() > 500)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    prefix + "lot number must be between 1 and 500");
            }
            if (transaction.lotNumber() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, prefix + "enter one lot number");
            }
        }
    }

    private ResponseEntity<Map<String, String>> clientSaveFailure(ResponseStatusException exception) {
        String reason = exception.getReason() == null ? "Client save was rejected" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("detail", reason));
    }

    private ResponseEntity<Map<String, String>> clientSaveFailure(DataAccessException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String reason = cause.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = exception.getClass().getSimpleName();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("detail", "Database error while saving client: " + reason));
    }

    private ResponseEntity<Map<String, String>> clientSaveFailure(TransactionException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String reason = cause.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = exception.getMessage();
        }
        if (reason == null || reason.isBlank()) {
            reason = exception.getClass().getSimpleName();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("detail", "Database transaction failed while saving client: " + reason));
    }

    private ResponseEntity<Map<String, String>> transactionSaveFailure(ResponseStatusException exception) {
        String reason = exception.getReason() == null ? "Transaction save was rejected" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("detail", reason));
    }

    private ResponseEntity<Map<String, String>> transactionSaveFailure(DataAccessException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String reason = cause.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = exception.getClass().getSimpleName();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("detail", "Database error while saving transaction: " + reason));
    }

    private ResponseEntity<Map<String, String>> transactionSaveFailure(TransactionException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String reason = cause.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = exception.getMessage();
        }
        if (reason == null || reason.isBlank()) {
            reason = exception.getClass().getSimpleName();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("detail", "Database transaction failed while saving transaction: " + reason));
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

    public record ClientWithTransactionsRequest(ClientRequest client, List<TransactionRequest> transactions) {
    }

    public record TransactionRequest(
            UUID id,
            String transactionType,
            Integer lotNumber,
            BigDecimal amount,
            LocalDate transactionDate,
            String method,
            String memo) {
    }

    public record LotRequest(String lotNumber, double price, String information) {
    }

    public record LotUpdateRequest(BigDecimal price, String information, Boolean paidInFull) {
    }
}
