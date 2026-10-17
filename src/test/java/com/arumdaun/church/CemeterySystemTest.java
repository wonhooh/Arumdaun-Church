package com.arumdaun.church;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CemeterySystemTest {
    private static final LocalDate PURCHASE_DATE = LocalDate.of(2026, 9, 5);
    private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID OTHER_CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final UUID UNKNOWN_CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private CemeterySystem system;

    @BeforeEach
    void setUp() {
        system = new CemeterySystem();
        system.addClient(CLIENT_ID, "김민수", "Kim", "Minsoo", "", "010-1234", null);
        system.addLot("A-1", 2500, "Corner");
        system.addLot("A-2", 1500);
    }

    @Test
    void purchaseLotsMarksLotsRecordsPaymentAndCalculatesBalance() {
        Purchase purchase = system.purchaseLots(CLIENT_ID, List.of("A-1", "A-2"), 0, PURCHASE_DATE);
        system.recordPayment(CLIENT_ID, 1000, "Cash", "", PURCHASE_DATE);

        assertEquals(4000, purchase.getTotalPrice());
        assertEquals(1000, purchase.getAmountPaid());
        assertTrue(system.getLot("A-1").isSold());
        assertEquals(CLIENT_ID, system.getLot("A-1").getClientId());
        assertEquals("A-1, A-2", system.getOwnedLotIds(CLIENT_ID));
        assertEquals(3000, system.getLotBalance("A-1"));
        assertEquals(1, system.getPayments().size());
    }

    @Test
    void purchaseRejectsMissingClientMissingLotAndSoldLot() {
        assertThrows(IllegalArgumentException.class,
                () -> system.purchaseLots(UNKNOWN_CLIENT_ID, List.of("A-1"), 0, PURCHASE_DATE));
        assertThrows(IllegalArgumentException.class,
                () -> system.purchaseLots(CLIENT_ID, List.of("A-9"), 0, PURCHASE_DATE));
            system.purchaseLots(CLIENT_ID, List.of("A-1"), 0, PURCHASE_DATE);
        assertThrows(IllegalStateException.class,
                () -> system.purchaseLots(CLIENT_ID, List.of("A-1"), 0, PURCHASE_DATE));
    }

    @Test
    void recordPaymentValidatesInputsAndUpdatesActivePurchase() {
        Purchase purchase = system.purchaseLots(CLIENT_ID, List.of("A-1"), 0, PURCHASE_DATE);

        assertThrows(IllegalArgumentException.class,
                () -> system.recordPayment(UNKNOWN_CLIENT_ID, 10, "Cash", "", PURCHASE_DATE));
        assertThrows(IllegalArgumentException.class,
                () -> system.recordPayment(CLIENT_ID, 0, "Cash", "", PURCHASE_DATE));
        assertThrows(IllegalArgumentException.class,
                () -> system.recordPayment(CLIENT_ID, 10, "Check", " ", PURCHASE_DATE));

        Payment payment = system.recordPayment(CLIENT_ID, 500, "Check", "1001", PURCHASE_DATE);

        assertEquals(500, purchase.getAmountPaid());
        assertEquals("A-1", payment.getLotIds());
        assertEquals("Check", payment.getMethod());
    }

    @Test
    void cancellingPurchaseReleasesLotsAndCreatesRefund() {
        Purchase purchase = system.purchaseLots(CLIENT_ID, List.of("A-1"), 0, PURCHASE_DATE);
        system.recordPayment(CLIENT_ID, 1000, "Cash", "", PURCHASE_DATE);

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
        system.addClient(OTHER_CLIENT_ID, "박지민", "Park", "Jimin", "", "111", null);
        system.softDeleteClient(OTHER_CLIENT_ID);

        assertEquals(1, system.getClients(false).size());
        assertEquals(2, system.getClients(true).size());
        assertThrows(IllegalArgumentException.class, () -> system.softDeleteClient(UNKNOWN_CLIENT_ID));
    }

    @Test
    void reportsContainPaymentsAndPurchases() {
        system.purchaseLots(CLIENT_ID, List.of("A-1"), 1000, PURCHASE_DATE);

        String report = system.getReportText();

        assertTrue(report.contains("CEMETERY SALES REPORT"));
        assertTrue(report.contains("Total sale value: $2,500.00"));
        assertTrue(report.contains("Total payments received: $1,000.00"));
        assertTrue(system.getPaymentHistoryText(UNKNOWN_CLIENT_ID).contains("No payment history found."));
        assertTrue(system.getClientTransactionText(UNKNOWN_CLIENT_ID).contains("No purchase records."));
    }
}