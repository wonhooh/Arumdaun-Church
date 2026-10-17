package com.arumdaun.church;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class PaymentTest {
        private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
        private static final UUID CLIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @Test
    void toStringFormatsMoneyAndIncludesCheckNumberForCheckPayments() {
        Payment payment = new Payment(PAYMENT_ID, CLIENT_ID, 1250.5, "Check", LocalDate.of(2026, 9, 5),
                "PAYMENT", "1001", "A-1");

        assertEquals(PAYMENT_ID + " | Client: " + CLIENT_ID + " | Type: PAYMENT | Method: Check | Amount: $1,250.50 "
                + "| Date: 2026-09-05 | Check No: 1001", payment.toString());
    }

    @Test
    void toStringOmitsCheckNumberForNonCheckPayments() {
        Payment payment = new Payment(PAYMENT_ID, CLIENT_ID, 25, "Cash", LocalDate.of(2026, 9, 5),
                "PAYMENT", "1001", "A-1");

        assertEquals(PAYMENT_ID + " | Client: " + CLIENT_ID + " | Type: PAYMENT | Method: Cash | Amount: $25.00 "
                + "| Date: 2026-09-05", payment.toString());
    }
}