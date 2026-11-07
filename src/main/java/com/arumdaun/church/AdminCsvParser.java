package com.arumdaun.church;

import java.util.ArrayList;
import java.util.List;

final class AdminCsvParser {
    private AdminCsvParser() {
    }

    static List<List<String>> parse(String csv) {
        if (csv.startsWith("\uFEFF")) {
            csv = csv.substring(1);
        }
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;

        for (int index = 0; index < csv.length(); index++) {
            char character = csv.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        afterQuote = true;
                    }
                } else {
                    field.append(character);
                }
                continue;
            }

            if (afterQuote && character != ',' && character != '\r' && character != '\n'
                    && !Character.isWhitespace(character)) {
                throw invalidCsv();
            }
            if (character == '"' && field.isEmpty() && !afterQuote) {
                quoted = true;
            } else if (character == '"') {
                throw invalidCsv();
            } else if (character == ',' || character == '\r' || character == '\n') {
                record.add(field.toString());
                field.setLength(0);
                afterQuote = false;
                if (character != ',') {
                    if (character == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') {
                        index++;
                    }
                    records.add(record);
                    record = new ArrayList<>();
                }
            } else if (afterQuote) {
                continue;
            } else {
                field.append(character);
            }
        }

        if (quoted) {
            throw invalidCsv();
        }
        if (!record.isEmpty() || !field.isEmpty() || afterQuote) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }

    private static IllegalArgumentException invalidCsv() {
        return new IllegalArgumentException("CSV contains invalid quote formatting");
    }
}
