package com.arumdaun.church;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Purchase implements Serializable {
    private static final long serialVersionUID = 1L;
    private final UUID purchaseId;
    private final UUID clientId;
    private final List<CemeterySystem.CemeteryLot> lots;
    private final double totalPrice;
    private double amountPaid;
    private boolean cancelled;
    private double refundAmount;
    private final LocalDate purchaseDate;
    private LocalDate cancellationDate;

    public Purchase(UUID purchaseId, UUID clientId, List<CemeterySystem.CemeteryLot> lots, double totalPrice,
            double amountPaid, LocalDate purchaseDate) {
        this.purchaseId = purchaseId;
        this.clientId = clientId;
        this.lots = new ArrayList<>(lots);
        this.totalPrice = totalPrice;
        this.amountPaid = amountPaid;
        this.purchaseDate = purchaseDate;
    }

    public Purchase(UUID clientId, List<CemeterySystem.CemeteryLot> lots, LocalDate purchaseDate, UUID purchaseId,
            double totalPrice) {
        this.clientId = clientId;
        this.lots = lots;
        this.purchaseDate = purchaseDate;
        this.purchaseId = purchaseId;
        this.totalPrice = totalPrice;
    }

    public UUID getPurchaseId() {
        return purchaseId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public List<CemeterySystem.CemeteryLot> getLots() {
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
        builder.append(" | Total: ").append(DisplayFormats.formatMoney(totalPrice))
                .append(" | Paid: ").append(DisplayFormats.formatMoney(amountPaid))
                .append(" | Purchase Date: ").append(DisplayFormats.formatDate(purchaseDate))
                .append(" | Status: ").append(cancelled ? "Cancelled" : "Active");
        if (cancelled) {
            builder.append(" | Cancel Date: ").append(DisplayFormats.formatDate(cancellationDate))
                    .append(" | Refund: ").append(DisplayFormats.formatMoney(refundAmount));
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return purchaseId + " | Client: " + clientId + " | Total: " + DisplayFormats.formatMoney(totalPrice)
                + " | Amount Paid: " + DisplayFormats.formatMoney(amountPaid) + " | "
                + (cancelled ? "Cancelled" : "Active");
    }
}
