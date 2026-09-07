package com.arumdaun.church;

import java.sql.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
        Integer clientId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(client_id), 99) + 1 FROM clients", Integer.class);
        jdbcTemplate.update("""
                INSERT INTO clients (client_id, korean_name, english_surname, english_given_name,
                    english_middle_name, phone1, phone2, street_address, city, state, zip_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, clientId, request.koreanName(), request.englishSurname(), request.englishGivenName(),
                request.englishMiddleName(), request.phone1(), request.phone2(), request.streetAddress(),
                request.city(), request.state(), request.zipCode());
        return loadSystem().getClients().get(clientId);
    }

    public Client updateClient(int clientId, CemeteryController.ClientRequest request) {
        int updated = jdbcTemplate.update("""
                UPDATE clients SET korean_name = ?, english_surname = ?, english_given_name = ?,
                    english_middle_name = ?, phone1 = ?, phone2 = ?, street_address = ?, city = ?,
                    state = ?, zip_code = ?
                WHERE client_id = ? AND deleted = FALSE
                """, request.koreanName(), request.englishSurname(), request.englishGivenName(),
                request.englishMiddleName(), request.phone1(), request.phone2(), request.streetAddress(),
                request.city(), request.state(), request.zipCode(), clientId);
        if (updated == 0) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }
        return loadSystem().getClients().get(clientId);
    }

    public CemeterySystem loadSystem() {
        CemeterySystem system = new CemeterySystem();
        Map<Integer, Client> clients = new LinkedHashMap<>();
        Map<String, CemeterySystem.CemeteryLot> lots = new LinkedHashMap<>();

        jdbcTemplate.query("""
                SELECT client_id, korean_name, english_surname, english_given_name,
                       english_middle_name, phone1, phone2, street_address, city,
                       state, zip_code, deleted, deleted_date
                FROM clients ORDER BY client_id
                """, rs -> {
            Client client = new Client(
                    rs.getInt("client_id"),
                    rs.getString("korean_name"),
                    rs.getString("english_surname"),
                    rs.getString("english_given_name"),
                    rs.getString("english_middle_name"),
                    rs.getString("phone1"),
                    rs.getString("phone2"),
                    rs.getString("street_address"),
                    rs.getString("city"),
                    rs.getString("state"),
                    rs.getString("zip_code"));
            if (rs.getBoolean("deleted")) {
                client.softDelete();
            }
            clients.put(client.getClientId(), client);
        });

        jdbcTemplate.query("""
                SELECT lot_number, price, information, sold, client_id
                FROM lots ORDER BY lot_number
                    """, rs -> {
            CemeterySystem.CemeteryLot lot = new CemeterySystem.CemeteryLot(
                    rs.getString("lot_number"),
                    rs.getDouble("price"),
                    rs.getString("information"));
            lot.setSold(rs.getBoolean("sold"));
            lot.setClientId((Integer) rs.getObject("client_id"));
            lots.put(lot.getLotNumber(), lot);
        });

        List<Purchase> purchases = new ArrayList<>();
        jdbcTemplate.query("""
                SELECT transaction_id, client_id, lot_numbers, total_price, amount_paid,
                       refund_amount, transaction_date, cancellation_date, cancelled
                FROM transactions
                WHERE transaction_type = 'PURCHASE'
                ORDER BY transaction_date, transaction_id
                """, rs -> {
            UUID purchaseId = rs.getObject("transaction_id", UUID.class);
            List<CemeterySystem.CemeteryLot> purchaseLots = new ArrayList<>();
            String[] lotNumbers = (String[]) rs.getArray("lot_numbers").getArray();
            for (String lotNumber : lotNumbers) {
                CemeterySystem.CemeteryLot lot = lots.get(lotNumber);
                if (lot != null) {
                    purchaseLots.add(lot);
                }
            }
            Purchase purchase = new Purchase(
                    purchaseId.toString(),
                    rs.getInt("client_id"),
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
                SELECT transaction_id, client_id, amount, method, transaction_date,
                       transaction_type, check_number, lot_numbers, refund_amount
                FROM transactions
                WHERE transaction_type IN ('PAYMENT', 'REFUND')
                ORDER BY transaction_date, transaction_id
                    """, (rs, rowNum) -> new Payment(
                rs.getObject("transaction_id", UUID.class).toString(),
                rs.getInt("client_id"),
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
        return system;
    }
}
