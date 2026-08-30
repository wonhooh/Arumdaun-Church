package com.arumdaun.church;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Purchase implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String purchaseId;
    private final int clientId;
    private final List<CemeterySystem.CemeteryLot> lots;
    private final double totalPrice;
    private double amountPaid;
    private boolean cancelled;
    private double refundAmount;
    private final LocalDate purchaseDate;
    private LocalDate cancellationDate;

    public Purchase(String purchaseId, int clientId, List<CemeterySystem.CemeteryLot> lots, double totalPrice,
            double amountPaid, LocalDate purchaseDate) {
        this.purchaseId = purchaseId;
        this.clientId = clientId;
        this.lots = new ArrayList<>(lots);
        this.totalPrice = totalPrice;
        this.amountPaid = amountPaid;
        this.purchaseDate = purchaseDate;
    }

    public Purchase(int clientId, List<CemeteryLot> lots, LocalDate purchaseDate, String purchaseId, double totalPrice) {
        this.clientId = clientId;
        this.lots = lots;
        this.purchaseDate = purchaseDate;
        this.purchaseId = purchaseId;
        this.totalPrice = totalPrice;
    }

    public String getPurchaseId() {
        return purchaseId;
    }

    public int getClientId() {
        return clientId;
    }

    public List<CemeteryLot> getLots() {
        return lots;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public double getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(double amountPaid) {
        this.amountPaid = amountPaid;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public double getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(double refundAmount) {
        this.refundAmount = refundAmount;
    }

    public void setCancellationDate(LocalDate cancellationDate) {
        this.cancellationDate = cancellationDate;
    }

    public void addPayment(double amount) {
        this.amountPaid += amount;
    }

    public String reportSummary() {
        StringBuilder builder = new StringBuilder();
        builder.append("Purchase ID: ").append(purchaseId)
                .append(" | Client: ").append(clientId)
                .append(" | Lots: ");
        for (int i = 0; i < lots.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(lots.get(i).getLotNumber());
        }
        builder.append(" | Total: ").append(formatMoney(totalPrice))
                .append(" | Paid: ").append(formatMoney(amountPaid))
                .append(" | Purchase Date: ").append(formatDate(purchaseDate))
                .append(" | Status: ").append(cancelled ? "Cancelled" : "Active");
        if (cancelled) {
            builder.append(" | Cancel Date: ").append(formatDate(cancellationDate))
                    .append(" | Refund: ").append(formatMoney(refundAmount));
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return purchaseId + " | Client: " + clientId + " | Total: " + formatMoney(totalPrice)
                + " | Amount Paid: " + formatMoney(amountPaid) + " | " + (cancelled ? "Cancelled" : "Active");
    }

    private String formatMoney(double value) {
        return String.format(Locale.US, "$%,.2f", value);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "Unknown" : date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
