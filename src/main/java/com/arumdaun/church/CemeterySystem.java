package com.arumdaun.church;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CemeterySystem implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, CemeteryLot> lots = new LinkedHashMap<>();
    private final Map<Integer, Client> clients = new LinkedHashMap<>();
    private final List<Purchase> purchases = new ArrayList<>();
    private final List<Payment> payments = new ArrayList<>();
    private int nextClientId = 102;

    public boolean isEmpty() {
        return clients.isEmpty() && lots.isEmpty() && purchases.isEmpty() && payments.isEmpty();
    }

    public void reconcileLotOwnership() {
        for (CemeteryLot lot : lots.values()) {
            lot.setSold(false);
            lot.setClientId(null);
        }
        for (Purchase purchase : purchases) {
            if (!purchase.isCancelled()) {
                for (CemeteryLot purchasedLot : purchase.getLots()) {
                    CemeteryLot storedLot = lots.get(purchasedLot.getLotNumber());
                    if (storedLot != null) {
                        storedLot.setSold(true);
                        storedLot.setClientId(purchase.getClientId());
                    }
                }
            }
        }
    }

    public int getNextClientId() {
        return nextClientId;
    }

    public Client getClient(int clientId) {
        return clients.get(clientId);
    }

    public List<Client> getClients() {
        return getClients(false);
    }

    public List<Client> getClients(boolean includeDeleted) {
        List<Client> result = new ArrayList<>(clients.values());
        if (!includeDeleted) {
            result.removeIf(Client::isDeleted);
        }
        result.sort(Comparator.comparing(Client::getEnglishFullName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public void softDeleteClient(int clientId) {
        Client client = clients.get(clientId);
        if (client == null) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }
        client.softDelete();
    }

    public List<Payment> getPayments() {
        return new ArrayList<>(payments);
    }

    public List<Purchase> getActivePurchasesForClient(int clientId) {
        List<Purchase> result = new ArrayList<>();
        for (Purchase purchase : purchases) {
            if (purchase.getClientId() == clientId && !purchase.isCancelled()) {
                result.add(purchase);
            }
        }
        return result;
    }

    public String getOwnedLotIds(int clientId) {
        StringBuilder result = new StringBuilder();
        for (Purchase purchase : getActivePurchasesForClient(clientId)) {
            for (CemeteryLot lot : purchase.getLots()) {
                if (result.length() > 0) {
                    result.append(", ");
                }
                result.append(lot.getLotNumber());
            }
        }
        return result.length() == 0 ? "None" : result.toString();
    }

    public boolean cancelLot(int clientId, String lotNumber, LocalDate cancelDate) {
        for (Purchase purchase : getActivePurchasesForClient(clientId)) {
            for (CemeteryLot lot : purchase.getLots()) {
                if (lot.getLotNumber().equalsIgnoreCase(lotNumber)) {
                    return cancelPurchase(purchase.getPurchaseId(), cancelDate);
                }
            }
        }
        throw new IllegalArgumentException("Selected lot was not found for this client.");
    }

    public double getLotBalance(String lotNumber) {
        for (Purchase purchase : purchases) {
            for (CemeteryLot lot : purchase.getLots()) {
                if (lot.getLotNumber().equalsIgnoreCase(lotNumber) && !purchase.isCancelled()) {
                    return Math.max(0.0, purchase.getTotalPrice() - purchase.getAmountPaid());
                }
            }
        }
        return 0.0;
    }

    public Client addClient(int clientId, String koreanName, String englishSurname, String englishGiven,
            String englishMiddle, String phone1, String phone2, String streetAddress, String city,
            String state, String zipCode) {
        Client client = new Client(clientId, koreanName, englishSurname, englishGiven, englishMiddle, phone1,
                phone2, streetAddress, city, state, zipCode);
        clients.put(clientId, client);
        if (clientId >= nextClientId) {
            nextClientId = clientId + 1;
        }
        return client;
    }

    public Client addClient(int clientId, String koreanName, String englishSurname, String englishGiven,
            String englishMiddle, String phone1, String phone2) {
        return addClient(clientId, koreanName, englishSurname, englishGiven, englishMiddle, phone1, phone2,
                "", "", "", "");
    }

    public void addLot(String lotNumber, double price) {
        addLot(lotNumber, price, "");
    }

    public void addLot(String lotNumber, double price, String information) {
        lots.put(lotNumber, new CemeteryLot(lotNumber, price, information));
    }

    public List<CemeteryLot> getAvailableLots() {
        List<CemeteryLot> available = new ArrayList<>();
        for (CemeteryLot lot : lots.values()) {
            if (!lot.isSold()) {
                available.add(lot);
            }
        }
        available.sort(Comparator.comparing(CemeteryLot::getLotNumber));
        return available;
    }

    public List<CemeteryLot> getLots() {
        List<CemeteryLot> result = new ArrayList<>(lots.values());
        result.sort(Comparator.comparing(CemeteryLot::getLotNumber));
        return result;
    }

    public CemeteryLot getLot(String lotNumber) {
        return lots.get(lotNumber);
    }

    public Purchase purchaseLots(int clientId, List<String> lotNumbers, double initialPayment,
            LocalDate purchaseDate) {
        Client client = clients.get(clientId);
        if (client == null) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }

        List<CemeteryLot> selectedLots = new ArrayList<>();
        for (String lotNumber : lotNumbers) {
            CemeteryLot lot = lots.get(lotNumber);
            if (lot == null) {
                throw new IllegalArgumentException("Lot not found: " + lotNumber);
            }
            if (lot.isSold()) {
                throw new IllegalStateException("Lot already sold: " + lotNumber);
            }
            selectedLots.add(lot);
        }

        double total = 0.0;
        for (CemeteryLot lot : selectedLots) {
            total += lot.getPrice();
            lot.setSold(true);
            lot.setClientId(clientId);
        }

        LocalDate effectivePurchaseDate = purchaseDate != null ? purchaseDate : LocalDate.now();
        Purchase purchase = new Purchase(
                UUID.randomUUID().toString(),
                clientId,
                selectedLots,
                total,
                initialPayment,
                effectivePurchaseDate);
        purchases.add(purchase);
        client.addPurchase(purchase);

        if (initialPayment > 0) {
            recordPayment(clientId, initialPayment, "Cash", "", effectivePurchaseDate);
        }
        return purchase;
    }

    public boolean cancelPurchase(String purchaseId, LocalDate cancelDate) {
        Purchase purchase = findPurchaseById(purchaseId);
        if (purchase == null || purchase.isCancelled()) {
            return false;
        }

        double refund = purchase.getAmountPaid();
        purchase.setCancelled(true);
        purchase.setRefundAmount(refund);
        purchase.setCancellationDate(cancelDate != null ? cancelDate : LocalDate.now());

        for (CemeteryLot lot : purchase.getLots()) {
            lot.setSold(false);
            lot.setClientId(null);
        }

        if (refund > 0) {
            Payment refundPayment = new Payment(
                    UUID.randomUUID().toString(),
                    purchase.getClientId(),
                    refund,
                    "Refund",
                    LocalDate.now(),
                    "REFUND",
                    "",
                    formatLotIds(purchase.getLots()));
            payments.add(refundPayment);
            purchase.setAmountPaid(0.0);
        }
        return true;
    }

    public Payment recordPayment(int clientId, double amount, String method, String checkNumber,
            LocalDate paymentDate) {
        if (!clients.containsKey(clientId)) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        if ("Check".equalsIgnoreCase(method) && (checkNumber == null || checkNumber.trim().isEmpty())) {
            throw new IllegalArgumentException("Check number is required for check payments.");
        }

        Payment payment = new Payment(
                UUID.randomUUID().toString(),
                clientId,
                amount,
                method,
                paymentDate != null ? paymentDate : LocalDate.now(),
                "PAYMENT",
                checkNumber,
                getActiveLotIds(clientId));
        payments.add(payment);

        for (Purchase purchase : purchases) {
            if (purchase.getClientId() == clientId && !purchase.isCancelled()) {
                purchase.addPayment(amount);
                break;
            }
        }
        return payment;
    }

    private String getActiveLotIds(int clientId) {
        for (Purchase purchase : purchases) {
            if (purchase.getClientId() == clientId && !purchase.isCancelled()) {
                return formatLotIds(purchase.getLots());
            }
        }
        return "";
    }

    private String formatLotIds(List<CemeteryLot> lots) {
        StringBuilder result = new StringBuilder();
        for (CemeteryLot lot : lots) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(lot.getLotNumber());
        }
        return result.toString();
    }

    public String getPaymentHistoryText(int clientId) {
        List<Payment> paymentsForClient = new ArrayList<>();
        for (Payment payment : payments) {
            if (payment.getClientId() == clientId) {
                paymentsForClient.add(payment);
            }
        }
        paymentsForClient.sort(Comparator.comparing(Payment::getDate).reversed());

        StringBuilder builder = new StringBuilder();
        builder.append("PAYMENT HISTORY FOR CLIENT: ").append(clientId).append("\n");
        if (paymentsForClient.isEmpty()) {
            builder.append("No payment history found.");
        } else {
            for (Payment payment : paymentsForClient) {
                builder.append(payment).append("\n");
            }
        }
        return builder.toString();
    }

    public String getClientTransactionText(int clientId) {
        StringBuilder builder = new StringBuilder();
        builder.append("PURCHASES AND CANCELLATIONS\n");
        boolean hasPurchase = false;
        for (Purchase purchase : purchases) {
            if (purchase.getClientId() == clientId) {
                builder.append(purchase.reportSummary()).append("\n");
                hasPurchase = true;
            }
        }
        if (!hasPurchase) {
            builder.append("No purchase records.\n");
        }

        builder.append("\nPAYMENTS\n");
        boolean hasPayment = false;
        for (Payment payment : payments) {
            if (payment.getClientId() == clientId) {
                builder.append(payment).append("\n");
                hasPayment = true;
            }
        }
        if (!hasPayment) {
            builder.append("No payment records.\n");
        }
        return builder.toString();
    }

    public String getReportText() {
        StringBuilder builder = new StringBuilder();
        builder.append("CEMETERY SALES REPORT\n");

        double totalSales = 0.0;
        double totalRefunds = 0.0;
        double totalPayments = 0.0;

        for (Purchase purchase : purchases) {
            totalSales += purchase.getTotalPrice();
            if (purchase.isCancelled()) {
                totalRefunds += purchase.getRefundAmount();
            }
        }

        for (Payment payment : payments) {
            if ("REFUND".equalsIgnoreCase(payment.getType())) {
                totalRefunds += payment.getAmount();
            } else {
                totalPayments += payment.getAmount();
            }
        }

        builder.append("Total sale value: ").append(formatMoney(totalSales)).append("\n");
        builder.append("Total payments received: ").append(formatMoney(totalPayments)).append("\n");
        builder.append("Total refunds issued: ").append(formatMoney(totalRefunds)).append("\n");
        builder.append("Net revenue: ").append(formatMoney(totalPayments - totalRefunds)).append("\n\n");
        builder.append("PURCHASE SUMMARY\n");
        if (purchases.isEmpty()) {
            builder.append("No purchase records yet.\n");
        } else {
            for (Purchase purchase : purchases) {
                builder.append(purchase.reportSummary()).append("\n");
            }
        }
        builder.append("\nPAYMENT HISTORY\n");
        if (payments.isEmpty()) {
            builder.append("No payments recorded yet.\n");
        } else {
            for (Payment payment : payments) {
                builder.append(payment).append("\n");
            }
        }
        return builder.toString();
    }

    private Purchase findPurchaseById(String purchaseId) {
        for (Purchase purchase : purchases) {
            if (purchase.getPurchaseId().equalsIgnoreCase(purchaseId)) {
                return purchase;
            }
        }
        return null;
    }

    private String formatMoney(double value) {
        return String.format(Locale.US, "$%,.2f", value);
    }

    static class CemeteryLot implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String lotNumber;
        private final double price;
        private boolean sold;
        private Integer clientId;
        private String information;

        public CemeteryLot(String lotNumber, double price, String information) {
            this.lotNumber = lotNumber;
            this.price = price;
            this.sold = false;
            this.clientId = null;
            this.information = information == null ? "" : information;
        }

        public String getLotNumber() {
            return lotNumber;
        }

        public double getPrice() {
            return price;
        }

        public boolean isSold() {
            return sold;
        }

        public void setSold(boolean sold) {
            this.sold = sold;
        }

        public Integer getClientId() {
            return clientId;
        }

        public void setClientId(Integer clientId) {
            this.clientId = clientId;
        }

        public String getInformation() {
            return information;
        }

        public void setInformation(String information) {
            this.information = information == null ? "" : information;
        }

        @Override
        public String toString() {
            return lotNumber + " | " + formatMoney(price) + " | " + (sold ? "Sold" : "Available");
        }

        private String formatMoney(double value) {
            return String.format(Locale.US, "$%,.2f", value);
        }
    }

}