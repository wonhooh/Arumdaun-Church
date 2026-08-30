package com.arumdaun.church;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class Payment implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String paymentId;
    private final int clientId;
    private final double amount;
    private final String method;
    private final LocalDate date;
    private final String type;
    private final String checkNumber;
    private final String lotIds;

    public Payment(String paymentId, int clientId, double amount, String method, LocalDate date,
            String type, String checkNumber, String lotIds) {
        this.paymentId = paymentId;
        this.clientId = clientId;
        this.amount = amount;
        this.method = method;
        this.date = date;
        this.type = type;
        this.checkNumber = checkNumber;
        this.lotIds = lotIds;
    }

    public int getClientId() {
        return clientId;
    }

    public double getAmount() {
        return amount;
    }

    public String getType() {
        return type;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getMethod() {
        return method;
    }

    public String getLotIds() {
        return lotIds;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(paymentId)
                .append(" | Client: ").append(clientId)
                .append(" | Type: ").append(type)
                .append(" | Method: ").append(method)
                .append(" | Amount: ").append(formatMoney(amount))
                .append(" | Date: ").append(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        if ("Check".equalsIgnoreCase(method) && checkNumber != null && !checkNumber.isEmpty()) {
            builder.append(" | Check No: ").append(checkNumber);
        }
        return builder.toString();
    }

    private String formatMoney(double value) {
        return String.format(Locale.US, "$%,.2f", value);
    }
}
