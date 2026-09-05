package com.arumdaun.church;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class PurchaseTest {

    @Test
    void reportSummaryFormatsActivePurchase() {
        CemeterySystem.CemeteryLot lot = new CemeterySystem.CemeteryLot("A-1", 2500, "Corner");
        Purchase purchase = new Purchase("purchase-1", 7, List.of(lot), 2500, 1000,
                LocalDate.of(2026, 9, 5));

        assertEquals("Purchase ID: purchase-1 | Client: 7 | Lots: A-1 | Total: $2,500.00 "
                + "| Paid: $1,000.00 | Purchase Date: 2026-09-05 | Status: Active",
                purchase.reportSummary());
        assertFalse(purchase.isCancelled());
    }

    @Test
    void reportSummaryIncludesCancellationDetailsAndNullDateFallback() {
        CemeterySystem.CemeteryLot lot = new CemeterySystem.CemeteryLot("A-1", 2500, "");
        Purchase purchase = new Purchase("purchase-1", 7, List.of(lot), 2500, 1000,
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
        Purchase purchase = new Purchase("purchase-1", 7, List.of(), 2500, 1000,
                LocalDate.of(2026, 9, 5));

        purchase.addPayment(500);

        assertEquals(1500, purchase.getAmountPaid());
    }
}