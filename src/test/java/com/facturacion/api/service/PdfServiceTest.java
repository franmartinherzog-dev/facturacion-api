package com.facturacion.api.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facturacion.api.exception.InvoiceNotFoundException;
import com.facturacion.api.model.Client;
import com.facturacion.api.model.Invoice;
import com.facturacion.api.model.User;
import com.facturacion.api.repository.InvoiceRepository;
import com.facturacion.api.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PdfServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private UserRepository userRepository;

    private PdfService pdfService;
    private User maria;

    @BeforeEach
    void setUp() {
        pdfService = new PdfService(invoiceRepository, userRepository);
        maria = new User("maria", "hash", "Maria SL", "B12345678", "Calle Mayor 1");
    }

    @Test
    void generateReturnsAValidPdfDocument() {
        Client acme = new Client("Acme SL", "B76543210", "Calle Sol 5", "c@example.com", "600123456", maria);
        Invoice invoice = new Invoice("2026-0001", LocalDate.of(2026, 10, 27), maria, acme,
                new BigDecimal("21"), new BigDecimal("15"), new BigDecimal("250.50"),
                new BigDecimal("52.61"), new BigDecimal("37.58"), new BigDecimal("265.53"));
        invoice.addLine("Diseño web", new BigDecimal("2"), new BigDecimal("100.00"));
        invoice.addLine("Hosting", new BigDecimal("1"), new BigDecimal("50.50"));
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(invoiceRepository.findByIdAndUser(1L, maria)).thenReturn(Optional.of(invoice));

        byte[] pdf = pdfService.generate("maria", 1L);

        // Every PDF file starts with the "%PDF" signature and ends with "%%EOF"
        assertEquals("%PDF", new String(Arrays.copyOf(pdf, 4), StandardCharsets.US_ASCII));
        String tail = new String(Arrays.copyOfRange(pdf, pdf.length - 16, pdf.length),
                StandardCharsets.US_ASCII);
        assertTrue(tail.contains("%%EOF"));
        assertTrue(pdf.length > 500);
    }

    @Test
    void generateRejectsAnInvoiceThatDoesNotBelongToTheUser() {
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(invoiceRepository.findByIdAndUser(9L, maria)).thenReturn(Optional.empty());

        assertThrows(InvoiceNotFoundException.class, () -> pdfService.generate("maria", 9L));
    }
}