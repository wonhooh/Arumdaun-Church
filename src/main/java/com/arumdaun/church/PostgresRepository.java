package com.arumdaun.church;

import java.sql.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class PostgresRepository {
    private final JdbcTemplate jdbcTemplate;

    public PostgresRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAvailable() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return true;
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
                UPDATE clients SET deleted_date = CURRENT_DATE
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
                       phone1, phone2, street_1, street_2, city, state, zip_code, deleted_date
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
            if (rs.getDate("deleted_date") != null) {
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
