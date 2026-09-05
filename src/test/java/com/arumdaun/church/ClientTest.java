package com.arumdaun.church;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ClientTest {

    @Test
    void constructorNormalizesNullAddressFieldsAndFormatsName() {
        Client client = new Client(7, "김민수", "Kim", "Minsoo", "", "010-1234", null,
                null, null, null, null);

        assertEquals(7, client.getClientId());
        assertEquals("Kim, Minsoo", client.getEnglishFullName());
        assertEquals("김민수 / Kim, Minsoo", client.getDisplayName());
        assertEquals("", client.getStreetAddress());
        assertEquals("", client.getCity());
        assertEquals("", client.getState());
        assertEquals("", client.getZipCode());
        assertFalse(client.isDeleted());
    }

    @Test
    void softDeleteMarksClientAndRecordsDate() {
        Client client = newClient();

        client.softDelete();

        assertTrue(client.isDeleted());
        assertEquals(LocalDate.now(), client.getDeletedDate());
    }

    @Test
    void updateDetailsReplacesAllEditableFields() {
        Client client = newClient();

        client.updateDetails("박지민", "Park", "Jimin", "S", "111", "222", "1 Main St",
                "Irvine", "CA", "92612");

        assertEquals("박지민", client.getKoreanName());
        assertEquals("Park, Jimin S", client.getEnglishFullName());
        assertEquals("111", client.getPhone1());
        assertEquals("222", client.getPhone2());
        assertEquals("1 Main St", client.getStreetAddress());
        assertEquals("Irvine", client.getCity());
        assertEquals("CA", client.getState());
        assertEquals("92612", client.getZipCode());
    }

    @Test
    void toStringIncludesClientAndPhoneDetails() {
        Client client = newClient();

        assertEquals("Client Id: 7 | Client Name: 김민수 / Kim, Minsoo | Phones: 010-1234, ",
                client.toString());
        assertEquals(null, client.getDeletedDate());
    }

    private Client newClient() {
        return new Client(7, "김민수", "Kim", "Minsoo", "", "010-1234", null,
                "1 Main St", "Irvine", "CA", "92612");
    }
}