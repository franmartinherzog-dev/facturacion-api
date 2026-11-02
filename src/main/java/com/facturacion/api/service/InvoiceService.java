package com.facturacion.api.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * Business logic for invoices: calculation of amounts, yearly numbering and
 * access restricted to the invoices of the authenticated user.
 *
 * The API is the only place where amounts are calculated; the client never sends them.
 */
@Service
public class InvoiceService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Amounts of an invoice, all already rounded to 2 decimals.
     *
     * @param taxableBase sum of the lines (quantity x unit price)
     * @param vatAmount VAT applied to the taxable base
     * @param irpfAmount IRPF withholding, subtracted from the total
     * @param total taxable base + VAT - IRPF
     */
    public record Totals(BigDecimal taxableBase, BigDecimal vatAmount,
                         BigDecimal irpfAmount, BigDecimal total) { }

    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          ClientRepository clientRepository,
                          UserRepository userRepository) {
        this.invoiceRepository = invoiceRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
    }

    /**
     * Lists the invoices of a user, newest first.
     *
     * @param username authenticated user
     * @return the user's invoices ordered by date and number, descending
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(String username) {
        User user = findUser(username);
        return invoiceRepository.findByUserOrderByIssueDateDescNumberDesc(user).stream()
                .map(InvoiceResponse::from)
                .toList();
    }

    /**
     * Returns one invoice of the user.
     *
     * @param username authenticated user
     * @param id invoice identifier
     * @return the invoice
     * @throws InvoiceNotFoundException if it does not exist or belongs to another user (404 in both cases,
     *         so the existence of other users' invoices is not revealed)
     */
    @Transactional(readOnly = true)
    public InvoiceResponse get(String username, Long id) {
        User user = findUser(username);
        return InvoiceResponse.from(invoiceRepository.findByIdAndUser(id, user)
                .orElseThrow(InvoiceNotFoundException::new));
    }

    /**
     * Creates an invoice: checks that the client belongs to the user, calculates the
     * amounts, assigns the next number of the year and saves it with its lines.
     *
     * @param username authenticated user
     * @param request client, date, tax rates and lines
     * @return the saved invoice, with the calculated amounts
     * @throws ClientNotFoundException if the client does not exist or belongs to another user
     */
    @Transactional
    public InvoiceResponse create(String username, InvoiceRequest request) {
        User user = findUser(username);
        Client client = clientRepository.findByIdAndUser(request.clientId(), user)
                .orElseThrow(ClientNotFoundException::new);

        Totals totals = calculateTotals(request.lines(), request.vatRate(), request.irpfRate());
        String number = nextNumber(user, request.issueDate().getYear());

        Invoice invoice = new Invoice(number, request.issueDate(), user, client,
                request.vatRate(), request.irpfRate(),
                totals.taxableBase(), totals.vatAmount(), totals.irpfAmount(), totals.total());
        request.lines().forEach(line ->
                invoice.addLine(line.description(), line.quantity(), line.unitPrice()));

        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }

    /**
     * Calculates the amounts of an invoice. The subtotals of the lines are added without
     * rounding and the taxable base is rounded only once (HALF_UP, 2 decimals); VAT and IRPF are
     * then rounded each from that base. Public and static so it can be unit-tested without Spring
     * or a database.
     *
     * @param lines invoice lines
     * @param vatRate VAT percentage, e.g. 21.00
     * @param irpfRate IRPF percentage, e.g. 15.00
     * @return the four amounts
     */
    public static Totals calculateTotals(List<InvoiceLineRequest> lines,
                                         BigDecimal vatRate, BigDecimal irpfRate) {
        // Sum the unrounded subtotals and round only once
        BigDecimal taxableBase = lines.stream()
                .map(line -> line.quantity().multiply(line.unitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal vatAmount = percentage(taxableBase, vatRate);
        BigDecimal irpfAmount = percentage(taxableBase, irpfRate);
        BigDecimal total = taxableBase.add(vatAmount).subtract(irpfAmount);

        return new Totals(taxableBase, vatAmount, irpfAmount, total);
    }

    private static BigDecimal percentage(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /**
     * Next number of the year for this user, in the format YYYY-NNNN: "2026-0001",
     * "2026-0002", ... The numbering restarts at 0001 each year and is independent for each user.
     *
     * @param user owner of the invoices
     * @param year year of the invoice date
     * @return the new invoice number
     */
    private String nextNumber(User user, int year) {
        String prefix = year + "-";
        int next = invoiceRepository
                .findTopByUserAndNumberStartingWithOrderByNumberDesc(user, prefix)
                .map(last -> Integer.parseInt(last.getNumber().substring(prefix.length())) + 1)
                .orElse(1);
        return String.format("%s%04d", prefix, next);
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(InvoiceNotFoundException::new);
    }
}