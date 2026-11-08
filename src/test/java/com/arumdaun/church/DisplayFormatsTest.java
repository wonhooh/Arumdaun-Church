package com.arumdaun.church;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class DisplayFormatsTest {
    @Test
    void formatsDatesAsMonthDayYear() {
        assertEquals("09/05/2026", DisplayFormats.formatDate(LocalDate.of(2026, 9, 5)));
        assertEquals("Unknown", DisplayFormats.formatDate(null));
    }

    @Test
    void formatsTenDigitUsPhoneNumbers() {
        assertEquals("631-456-1111", DisplayFormats.formatPhone("6314561111"));
        assertEquals("666-444-0000", DisplayFormats.formatPhone("1 (666) 444-0000"));
        assertEquals("unknown", DisplayFormats.formatPhone("unknown"));
    }

    @Test
    void formatsMoneyAsWholeDollars() {
        assertEquals("$1,251", DisplayFormats.formatMoney(1250.5));
        assertEquals("$1,251", DisplayFormats.formatMoney(new BigDecimal("1250.50")));
        assertEquals("-$25", DisplayFormats.formatMoney(-25));
    }
}
