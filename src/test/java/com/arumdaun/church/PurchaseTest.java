package com.arumdaun.church;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PurchaseTest {
    private static final UUID PURCHASE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @Test
    void reportSummaryFormatsActivePurchase() {
        CemeterySystem.CemeteryLot lot = new CemeterySystem.CemeteryLot("A-1", 2500, "Corner");
        Purchase purchase = new Purchase(PURCHASE_ID, CLIENT_ID, List.of(lot), 2500, 1000,
                LocalDate.of(2026, 9, 5));

        assertEquals("Purchase ID: " + PURCHASE_ID + " | Client: " + CLIENT_ID + " | Lots: A-1 | Total: $2,500.00 "
                + "| Paid: $1,000.00 | Purchase Date: 2026-09-05 | Status: Active",
                purchase.reportSummary());
        assertFalse(purchase.isCancelled());
    }

    @Test
    void reportSummaryIncludesCancellationDetailsAndNullDateFallback() {
        CemeterySystem.CemeteryLot lot = new CemeterySystem.CemeteryLot("A-1", 2500, "");
        Purchase purchase = new Purchase(PURCHASE_ID, CLIENT_ID, List.of(lot), 2500, 1000,
                LocalDate.of(2026, 9, 5));
        purchase.setCancelled(true);
        purchase.setRefundAmount(1000);
        purchase.setCancellationDate(null);

        assertTrue(purchase.reportSummary().contains("Status: Cancelled"));
        assertTrue(purchase.reportSummary().contains("Cancel Date: Unknown"));
        assertTrue(purchase.reportSummary().contains("Refund: $1,000.00"));
    }

    @Test
    void addPaymentIncreasesAmountPaid() {
        Purchase purchase = new Purchase(PURCHASE_ID, CLIENT_ID, List.of(), 2500, 1000,
                LocalDate.of(2026, 9, 5));

        purchase.addPayment(500);

        assertEquals(1500, purchase.getAmountPaid());
    }
}