package com.facturacion.api.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facturacion.api.exception.InvoiceNotFoundException;
import com.facturacion.api.model.Client;
import com.facturacion.api.model.Invoice;
import com.facturacion.api.model.InvoiceLine;
import com.facturacion.api.model.User;
import com.facturacion.api.repository.InvoiceRepository;
import com.facturacion.api.repository.UserRepository;

@Service
public class PdfService {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale SPAIN = Locale.of("es", "ES");

    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    public PdfService(InvoiceRepository invoiceRepository, UserRepository userRepository) {
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public byte[] generate(String username, Long invoiceId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(InvoiceNotFoundException::new);
        Invoice invoice = invoiceRepository.findByIdAndUser(invoiceId, user)
                .orElseThrow(InvoiceNotFoundException::new);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph title = new Paragraph("FACTURA", TITLE);
            title.setSpacingAfter(8);
            document.add(title);
            document.add(new Paragraph("Nº " + invoice.getNumber(), BOLD));
            Paragraph date = new Paragraph("Fecha: " + invoice.getIssueDate().format(DATE_FORMAT), NORMAL);
            date.setSpacingAfter(20);
            document.add(date);

            document.add(partiesTable(invoice.getUser(), invoice.getClient()));
            document.add(linesTable(invoice));
            document.add(totalsTable(invoice));

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        }
        return out.toByteArray();
    }

    // Issuer on the left, client on the right
    private PdfPTable partiesTable(User issuer, Client client) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(20);
        table.addCell(partyCell("Emisor", issuer.getCompanyName(), issuer.getTaxId(),
                issuer.getAddress(), null));
        table.addCell(partyCell("Cliente", client.getName(), client.getTaxId(),
                client.getAddress(), client.getEmail()));
        return table;
    }

    private PdfPCell partyCell(String heading, String name, String taxId, String address, String email) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.addElement(new Paragraph(heading, BOLD));
        cell.addElement(new Paragraph(name, NORMAL));
        addIfPresent(cell, "NIF/CIF: ", taxId);
        addIfPresent(cell, "", address);
        addIfPresent(cell, "", email);
        return cell;
    }

    private void addIfPresent(PdfPCell cell, String prefix, String value) {
        if (value != null && !value.isBlank()) {
            cell.addElement(new Paragraph(prefix + value, NORMAL));
        }
    }

    private PdfPTable linesTable(Invoice invoice) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingAfter(15);
        try {
            table.setWidths(new float[] {50, 12, 19, 19});
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        }

        table.addCell(cell("Descripción", BOLD, Element.ALIGN_LEFT, Rectangle.BOTTOM));
        table.addCell(cell("Cantidad", BOLD, Element.ALIGN_RIGHT, Rectangle.BOTTOM));
        table.addCell(cell("Precio unitario", BOLD, Element.ALIGN_RIGHT, Rectangle.BOTTOM));
        table.addCell(cell("Importe", BOLD, Element.ALIGN_RIGHT, Rectangle.BOTTOM));

        for (InvoiceLine line : invoice.getLines()) {
            table.addCell(cell(line.getDescription(), NORMAL, Element.ALIGN_LEFT, Rectangle.NO_BORDER));
            table.addCell(cell(quantity(line.getQuantity()), NORMAL, Element.ALIGN_RIGHT, Rectangle.NO_BORDER));
            table.addCell(cell(money(line.getUnitPrice()), NORMAL, Element.ALIGN_RIGHT, Rectangle.NO_BORDER));
            table.addCell(cell(money(line.getSubtotal()), NORMAL, Element.ALIGN_RIGHT, Rectangle.NO_BORDER));
        }
        return table;
    }

    private PdfPTable totalsTable(Invoice invoice) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(45);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(cell("Base imponible", NORMAL, Element.ALIGN_LEFT, Rectangle.NO_BORDER));
        table.addCell(cell(money(invoice.getTaxableBase()), NORMAL, Element.ALIGN_RIGHT, Rectangle.NO_BORDER));

        table.addCell(cell("IVA (" + percent(invoice.getVatRate()) + " %)", NORMAL,
                Element.ALIGN_LEFT, Rectangle.NO_BORDER));
        table.addCell(cell(money(invoice.getVatAmount()), NORMAL, Element.ALIGN_RIGHT, Rectangle.NO_BORDER));

        if (invoice.getIrpfRate().signum() > 0) {
            table.addCell(cell("IRPF (-" + percent(invoice.getIrpfRate()) + " %)", NORMAL,
                    Element.ALIGN_LEFT, Rectangle.NO_BORDER));
            table.addCell(cell(money(invoice.getIrpfAmount().negate()), NORMAL,
                    Element.ALIGN_RIGHT, Rectangle.NO_BORDER));
        }

        table.addCell(cell("TOTAL", BOLD, Element.ALIGN_LEFT, Rectangle.TOP));
        table.addCell(cell(money(invoice.getTotal()), BOLD, Element.ALIGN_RIGHT, Rectangle.TOP));
        return table;
    }

    private PdfPCell cell(String text, Font font, int alignment, int border) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setBorder(border);
        cell.setPadding(4);
        return cell;
    }

    // Spanish format: 1.234,56 €
    private static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(SPAIN);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount.setScale(2, RoundingMode.HALF_UP)) + " €";
    }

    private static String quantity(BigDecimal quantity) {
        NumberFormat format = NumberFormat.getNumberInstance(SPAIN);
        format.setMaximumFractionDigits(2);
        return format.format(quantity);
    }

    // 21.00 -> "21", 10.50 -> "10.5"
    private static String percent(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString();
    }
}