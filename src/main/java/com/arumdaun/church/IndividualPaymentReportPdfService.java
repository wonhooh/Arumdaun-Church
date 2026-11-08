package com.arumdaun.church;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

@Service
public class IndividualPaymentReportPdfService {
    private static final int RECORDS_PER_PAGE = 10;
    private static final Color HEADER_BACKGROUND = new Color(232, 238, 232);
    private static final Color BORDER_COLOR = new Color(37, 42, 39);

    public byte[] generate(List<PostgresRepository.IndividualPaymentReportLot> lots)
            throws IOException, DocumentException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BaseFont baseFont = BaseFont.createFont(
                "HYSMyeongJo-Medium", "UniKS-UCS2-H", BaseFont.NOT_EMBEDDED);
        Document document = new Document(PageSize.LETTER.rotate(), 24, 24, 24, 42);
        PdfWriter writer = PdfWriter.getInstance(document, output);
        writer.setPageEvent(new PrintDateFooter(baseFont, DisplayFormats.formatDate(LocalDate.now())));
        document.open();

        FontSet fonts = new FontSet(baseFont);
        int pageCount = Math.max(1, (lots.size() + RECORDS_PER_PAGE - 1) / RECORDS_PER_PAGE);
        for (int page = 0; page < pageCount; page++) {
            if (page > 0) {
                document.newPage();
            }
            addPageTitle(document, fonts.title());
            PdfPTable table = createTable(fonts);
            int start = page * RECORDS_PER_PAGE;
            int end = Math.min(start + RECORDS_PER_PAGE, lots.size());
            for (int index = start; index < end; index++) {
                addLotRow(table, lots.get(index), fonts.body());
            }
            if (lots.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("No lots found.", fonts.body()));
                empty.setColspan(8);
                empty.setPadding(8);
                table.addCell(empty);
            }
            document.add(table);
        }
        document.close();
        return output.toByteArray();
    }

    private void addPageTitle(Document document, Font titleFont) throws DocumentException {
        com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph(
                "** INDIVIDUAL PAYMENT RECORD **", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(9);
        document.add(title);
    }

    private PdfPTable createTable(FontSet fonts) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[] { 8, 17, 9, 12, 12, 12, 12, 18 });
        table.setWidthPercentage(100);
        table.setHeaderRows(2);
        table.setSplitRows(false);
        table.setSplitLate(false);

        addHeaderCell(table, "LOT NO.", fonts.header(), 1, 2);
        addHeaderCell(table, "NAME", fonts.header(), 1, 2);
        addHeaderCell(table, "PRICE", fonts.header(), 1, 2);
        addHeaderCell(table, "PAYMENT RECORD", fonts.header(), 4, 1);
        addHeaderCell(table, "REMARK", fonts.header(), 1, 2);
        for (String paymentNumber : List.of("1st", "2nd", "3rd", "4th")) {
            addHeaderCell(table, paymentNumber, fonts.header(), 1, 1);
        }
        return table;
    }

    private void addHeaderCell(PdfPTable table, String text, Font font, int colspan, int rowspan) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setColspan(colspan);
        cell.setRowspan(rowspan);
        cell.setBackgroundColor(HEADER_BACKGROUND);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        cell.setBorderColor(BORDER_COLOR);
        table.addCell(cell);
    }

    private void addLotRow(PdfPTable table, PostgresRepository.IndividualPaymentReportLot lot, Font bodyFont) {
        addBodyCell(table, String.format(java.util.Locale.ROOT, "%03d", lot.lotNumber()), bodyFont,
                Element.ALIGN_CENTER);
        addBodyCell(table, lot.name(), bodyFont, Element.ALIGN_LEFT);
        addBodyCell(table, DisplayFormats.formatMoney(lot.price()), bodyFont, Element.ALIGN_CENTER);
        for (int index = 0; index < 4; index++) {
            String content = index < lot.payments().size() ? formatPayment(lot.payments().get(index)) : "";
            addBodyCell(table, content, bodyFont, Element.ALIGN_CENTER);
        }
        addBodyCell(table, lot.remarks(), bodyFont, Element.ALIGN_LEFT);
    }

    private String formatPayment(PostgresRepository.ReportTransaction payment) {
        StringBuilder text = new StringBuilder(payment.type());
        if (payment.date() != null) {
            text.append('\n').append(DisplayFormats.formatDate(payment.date()));
        }
        if (payment.amount() != null) {
            text.append('\n').append(DisplayFormats.formatMoney(payment.amount()));
        }
        if (payment.method() != null && !payment.method().isBlank()) {
            text.append('\n').append(payment.method());
        }
        return text.toString();
    }

    private void addBodyCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        cell.setBorderColor(BORDER_COLOR);
        table.addCell(cell);
    }

    private record FontSet(Font title, Font header, Font body) {
        FontSet(BaseFont baseFont) {
            this(new Font(baseFont, 12, Font.BOLD),
                    new Font(baseFont, 8, Font.BOLD),
                    new Font(baseFont, 7, Font.NORMAL));
        }
    }

    private static final class PrintDateFooter extends PdfPageEventHelper {
        private final BaseFont baseFont;
        private final String date;

        private PrintDateFooter(BaseFont baseFont, String date) {
            this.baseFont = baseFont;
            this.date = date;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                    new Phrase("Printed on " + date, new Font(baseFont, 8)),
                    document.left(), 18, 0);
        }
    }
}
