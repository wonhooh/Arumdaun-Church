package com.arumdaun.church;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared display formats for dates, US phone numbers, and whole-dollar amounts. */
public final class DisplayFormats {
    public static final String DATE_PATTERN = "MM/dd/yyyy";
    public static final String PHONE_NUMBER_PATTERN = "\\d{3}-\\d{3}-\\d{4}";
    public static final String MONEY_PATTERN = "$#,##0;-$#,##0";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);
    private static final Pattern PHONE_DIGITS = Pattern.compile("\\D");

    private DisplayFormats() {
    }

    public static String formatDate(LocalDate date) {
        return date == null ? "Unknown" : DATE_FORMATTER.format(date);
    }

    public static String formatPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = PHONE_DIGITS.matcher(phone).replaceAll("");
        if (digits.length() == 11 && digits.startsWith("1")) {
            digits = digits.substring(1);
        }
        if (digits.length() != 10) {
            return phone;
        }
        return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
    }

    public static String formatMoney(double amount) {
        DecimalFormat formatter = new DecimalFormat(MONEY_PATTERN, DecimalFormatSymbols.getInstance(Locale.US));
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        return formatter.format(amount);
    }

    public static String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        DecimalFormat formatter = new DecimalFormat(MONEY_PATTERN, DecimalFormatSymbols.getInstance(Locale.US));
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        return formatter.format(amount);
    }
}
