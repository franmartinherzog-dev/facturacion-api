package com.facturacion.api.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facturacion.api.dto.InvoiceLineRequest;
import com.facturacion.api.dto.InvoiceRequest;
import com.facturacion.api.dto.InvoiceResponse;
import com.facturacion.api.exception.ClientNotFoundException;
import com.facturacion.api.exception.InvoiceNotFoundException;
import com.facturacion.api.model.Client;
import com.facturacion.api.model.Invoice;
import com.facturacion.api.model.User;
import com.facturacion.api.repository.ClientRepository;
import com.facturacion.api.repository.InvoiceRepository;
import com.facturacion.api.repository.UserRepository;
import com.facturacion.api.service.InvoiceService.Totals;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private UserRepository userRepository;

    private InvoiceService invoiceService;
    private User maria;
    private Client acme;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository, clientRepository, userRepository);
        maria = new User("maria", "hash", "Maria SL", "B12345678", "Calle Mayor 1");
        acme = new Client("Acme SL", "B76543210", "Calle Sol 5", "c@example.com", "600123456", maria);
    }

    private static InvoiceLineRequest line(String description, String quantity, String unitPrice) {
        return new InvoiceLineRequest(description, new BigDecimal(quantity), new BigDecimal(unitPrice));
    }

    private static InvoiceRequest request(LocalDate date, InvoiceLineRequest... lines) {
        return new InvoiceRequest(5L, date, new BigDecimal("21"), new BigDecimal("15"), List.of(lines));
    }

    // ---- calculateTotals ----

    @Test
    void calculateTotalsAppliesVatAndIrpfWithRounding() {
        Totals totals = InvoiceService.calculateTotals(
                List.of(line("Web", "2", "100.00"), line("Hosting", "1", "50.50")),
                new BigDecimal("21"), new BigDecimal("15"));

        assertEquals(new BigDecimal("250.50"), totals.taxableBase());
        assertEquals(new BigDecimal("52.61"), totals.vatAmount());   // 52.605 rounds up
        assertEquals(new BigDecimal("37.58"), totals.irpfAmount());  // 37.575 rounds up
        assertEquals(new BigDecimal("265.53"), totals.total());
    }

    @Test
    void calculateTotalsRoundsOnlyOnceOnTheSum() {
        // Each line is 0.015; rounding each one would give 0.02 + 0.02 = 0.04
        Totals totals = InvoiceService.calculateTotals(
                List.of(line("A", "1.50", "0.01"), line("B", "1.50", "0.01")),
                BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(new BigDecimal("0.03"), totals.taxableBase());
    }

    @Test
    void calculateTotalsWithZeroIrpfDoesNotWithholdAnything() {
        Totals totals = InvoiceService.calculateTotals(
                List.of(line("Service", "1", "100.00")),
                new BigDecimal("21"), BigDecimal.ZERO);

        assertEquals(new BigDecimal("0.00"), totals.irpfAmount());
        assertEquals(new BigDecimal("121.00"), totals.total());
    }

    // ---- create ----

    @Test
    void createAssignsTheFirstNumberOfTheYear() {
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(clientRepository.findByIdAndUser(5L, maria)).thenReturn(Optional.of(acme));
        when(invoiceRepository.findTopByUserAndNumberStartingWithOrderByNumberDesc(maria, "2026-"))
                .thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(call -> call.getArgument(0));

        InvoiceResponse response = invoiceService.create("maria",
                request(LocalDate.of(2026, 10, 27), line("Web", "1", "100.00")));

        assertEquals("2026-0001", response.number());
        assertEquals(new BigDecimal("106.00"), response.total());
        assertEquals(1, response.lines().size());
    }

    @Test
    void createContinuesTheNumberingOfTheYear() {
        Invoice last = new Invoice("2026-0007", LocalDate.of(2026, 9, 1), maria, acme,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(clientRepository.findByIdAndUser(5L, maria)).thenReturn(Optional.of(acme));
        when(invoiceRepository.findTopByUserAndNumberStartingWithOrderByNumberDesc(maria, "2026-"))
                .thenReturn(Optional.of(last));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(call -> call.getArgument(0));

        InvoiceResponse response = invoiceService.create("maria",
                request(LocalDate.of(2026, 10, 27), line("Web", "1", "100.00")));

        assertEquals("2026-0008", response.number());
    }

    @Test
    void createTakesTheYearFromTheIssueDate() {
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(clientRepository.findByIdAndUser(5L, maria)).thenReturn(Optional.of(acme));
        when(invoiceRepository.findTopByUserAndNumberStartingWithOrderByNumberDesc(maria, "2025-"))
                .thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(call -> call.getArgument(0));

        InvoiceResponse response = invoiceService.create("maria",
                request(LocalDate.of(2025, 12, 31), line("Web", "1", "100.00")));

        assertEquals("2025-0001", response.number());
    }

    @Test
    void createRejectsAClientThatDoesNotBelongToTheUserAndSavesNothing() {
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(clientRepository.findByIdAndUser(5L, maria)).thenReturn(Optional.empty());

        assertThrows(ClientNotFoundException.class, () -> invoiceService.create("maria",
                request(LocalDate.of(2026, 10, 27), line("Web", "1", "100.00"))));
        verify(invoiceRepository, never()).save(any());
    }

    // ---- get / list ----

    @Test
    void getRejectsAnInvoiceThatDoesNotBelongToTheUser() {
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(invoiceRepository.findByIdAndUser(9L, maria)).thenReturn(Optional.empty());

        assertThrows(InvoiceNotFoundException.class, () -> invoiceService.get("maria", 9L));
    }

    @Test
    void listReturnsOnlyTheInvoicesOfTheUser() {
        Invoice invoice = new Invoice("2026-0001", LocalDate.of(2026, 10, 1), maria, acme,
                new BigDecimal("21"), new BigDecimal("15"), new BigDecimal("100.00"),
                new BigDecimal("21.00"), new BigDecimal("15.00"), new BigDecimal("106.00"));
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(invoiceRepository.findByUserOrderByIssueDateDescNumberDesc(maria))
                .thenReturn(List.of(invoice));

        List<InvoiceResponse> result = invoiceService.list("maria");

        assertEquals(1, result.size());
        assertEquals("2026-0001", result.get(0).number());
    }
}