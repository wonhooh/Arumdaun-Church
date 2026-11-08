package com.arumdaun.church;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndividualPaymentReportPdfServiceTest {
    private final IndividualPaymentReportPdfService service = new IndividualPaymentReportPdfService();

    @Test
    void createsPdfWithTenLotRecordsPerPage() throws IOException, DocumentException {
        List<PostgresRepository.IndividualPaymentReportLot> lots = new ArrayList<>();
        for (int lotNumber = 1; lotNumber <= 11; lotNumber++) {
            lots.add(new PostgresRepository.IndividualPaymentReportLot(
                    lotNumber,
                    "홍길동 / Gil Dong Hong",
                    new BigDecimal("700.00"),
                    List.of(new PostgresRepository.ReportTransaction(
                            "PURCHASE", new BigDecimal("700.00"), "Cash", LocalDate.of(2026, 11, 8), "")),
                    ""));
        }

        byte[] pdf = service.generate(lots);
        PdfReader reader = new PdfReader(pdf);
        try {
            assertTrue(pdf.length > 4);
            assertEquals('%', (char) pdf[0]);
            assertEquals(2, reader.getNumberOfPages());
        } finally {
            reader.close();
        }
    }

    @Test
    void createsAnEmptyReportPdf() throws IOException, DocumentException {
        byte[] pdf = service.generate(List.of());
        PdfReader reader = new PdfReader(pdf);
        try {
            assertEquals(1, reader.getNumberOfPages());
        } finally {
            reader.close();
        }
    }
}
