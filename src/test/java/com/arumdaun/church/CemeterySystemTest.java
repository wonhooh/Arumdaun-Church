package com.arumdaun.church;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CemeterySystemTest {
    private static final LocalDate PURCHASE_DATE = LocalDate.of(2026, 9, 5);

    private CemeterySystem system;

    @BeforeEach
    void setUp() {
        system = new CemeterySystem();
        system.addClient(7, "김민수", "Kim", "Minsoo", "", "010-1234", null);
        system.addLot("A-1", 2500, "Corner");
        system.addLot("A-2", 1500);
    }

    @Test
    void purchaseLotsMarksLotsRecordsPaymentAndCalculatesBalance() {
        Purchase purchase = system.purchaseLots(7, List.of("A-1", "A-2"), 0, PURCHASE_DATE);
        system.recordPayment(7, 1000, "Cash", "", PURCHASE_DATE);

        assertEquals(4000, purchase.getTotalPrice());
        assertEquals(1000, purchase.getAmountPaid());
        assertTrue(system.getLot("A-1").isSold());
        assertEquals(7, system.getLot("A-1").getClientId());
        assertEquals("A-1, A-2", system.getOwnedLotIds(7));
        assertEquals(3000, system.getLotBalance("A-1"));
        assertEquals(1, system.getPayments().size());
    }

    @Test
    void purchaseRejectsMissingClientMissingLotAndSoldLot() {
        assertThrows(IllegalArgumentException.class,
                () -> system.purchaseLots(99, List.of("A-1"), 0, PURCHASE_DATE));
        assertThrows(IllegalArgumentException.class,
                () -> system.purchaseLots(7, List.of("A-9"), 0, PURCHASE_DATE));
        system.purchaseLots(7, List.of("A-1"), 0, PURCHASE_DATE);
        assertThrows(IllegalStateException.class,
                () -> system.purchaseLots(7, List.of("A-1"), 0, PURCHASE_DATE));
    }

    @Test
    void recordPaymentValidatesInputsAndUpdatesActivePurchase() {
        Purchase purchase = system.purchaseLots(7, List.of("A-1"), 0, PURCHASE_DATE);

        assertThrows(IllegalArgumentException.class,
                () -> system.recordPayment(99, 10, "Cash", "", PURCHASE_DATE));
        assertThrows(IllegalArgumentException.class,
                () -> system.recordPayment(7, 0, "Cash", "", PURCHASE_DATE));
        assertThrows(IllegalArgumentException.class,
                () -> system.recordPayment(7, 10, "Check", " ", PURCHASE_DATE));

        Payment payment = system.recordPayment(7, 500, "Check", "1001", PURCHASE_DATE);

        assertEquals(500, purchase.getAmountPaid());
        assertEquals("A-1", payment.getLotIds());
        assertEquals("Check", payment.getMethod());
    }

    @Test
    void cancellingPurchaseReleasesLotsAndCreatesRefund() {
        Purchase purchase = system.purchaseLots(7, List.of("A-1"), 0, PURCHASE_DATE);
        system.recordPayment(7, 1000, "Cash", "", PURCHASE_DATE);

        assertTrue(system.cancelPurchase(purchase.getPurchaseId(), PURCHASE_DATE));

        assertTrue(purchase.isCancelled());
        assertEquals(1000, purchase.getRefundAmount());
        assertEquals(0, purchase.getAmountPaid());
        assertFalse(system.getLot("A-1").isSold());
        assertEquals(2, system.getPayments().size());
        assertFalse(system.cancelPurchase(purchase.getPurchaseId(), PURCHASE_DATE));
    }

    @Test
    void clientListsExcludeDeletedClientsUnlessRequested() {
        system.addClient(8, "박지민", "Park", "Jimin", "", "111", null);
        system.softDeleteClient(8);

        assertEquals(1, system.getClients(false).size());
        assertEquals(2, system.getClients(true).size());
        assertThrows(IllegalArgumentException.class, () -> system.softDeleteClient(99));
    }

    @Test
    void reportsContainPaymentsAndPurchases() {
        system.purchaseLots(7, List.of("A-1"), 1000, PURCHASE_DATE);

        String report = system.getReportText();

        assertTrue(report.contains("CEMETERY SALES REPORT"));
        assertTrue(report.contains("Total sale value: $2,500.00"));
        assertTrue(report.contains("Total payments received: $1,000.00"));
        assertTrue(system.getPaymentHistoryText(99).contains("No payment history found."));
        assertTrue(system.getClientTransactionText(99).contains("No purchase records."));
    }
}