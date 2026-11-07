package com.arumdaun.church;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class AdminCsvParserTest {
    @Test
    void parsesBomEscapedQuotesCommasAndMultilineValues() {
        List<List<String>> rows = AdminCsvParser.parse(
            "\uFEFF\"id\",\"information\"\r\n1,\"Corner, \"\"quiet\"\" lot\nnear the garden\"\r\n");

        assertEquals(List.of(
                List.of("id", "information"),
                List.of("1", "Corner, \"quiet\" lot\nnear the garden")), rows);
    }

    @Test
    void rejectsUnclosedQuotedField() {
        assertThrows(IllegalArgumentException.class, () -> AdminCsvParser.parse("id,name\n1,\"unfinished"));
    }
}
