package com.arumdaun.church;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    void toStringFormatsMoneyAndIncludesCheckNumberForCheckPayments() {
        Payment payment = new Payment("payment-1", 7, 1250.5, "Check", LocalDate.of(2026, 9, 5),
                "PAYMENT", "1001", "A-1");

        assertEquals("payment-1 | Client: 7 | Type: PAYMENT | Method: Check | Amount: $1,250.50 "
                + "| Date: 2026-09-05 | Check No: 1001", payment.toString());
    }

    @Test
    void toStringOmitsCheckNumberForNonCheckPayments() {
        Payment payment = new Payment("payment-2", 7, 25, "Cash", LocalDate.of(2026, 9, 5),
                "PAYMENT", "1001", "A-1");

        assertEquals("payment-2 | Client: 7 | Type: PAYMENT | Method: Cash | Amount: $25.00 "
                + "| Date: 2026-09-05", payment.toString());
    }
}