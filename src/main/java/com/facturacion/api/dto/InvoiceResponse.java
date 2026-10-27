package com.facturacion.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.facturacion.api.model.Invoice;

public record InvoiceResponse(
        Long id,
        String number,
        LocalDate issueDate,
        Long clientId,
        String clientName,
        BigDecimal vatRate,
        BigDecimal irpfRate,
        BigDecimal taxableBase,
        BigDecimal vatAmount,
        BigDecimal irpfAmount,
        BigDecimal total,
        List<InvoiceLineResponse> lines
) {

    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getNumber(),
                invoice.getIssueDate(),
                invoice.getClient().getId(),
                invoice.getClient().getName(),
                invoice.getVatRate(),
                invoice.getIrpfRate(),
                invoice.getTaxableBase(),
                invoice.getVatAmount(),
                invoice.getIrpfAmount(),
                invoice.getTotal(),
                invoice.getLines().stream().map(InvoiceLineResponse::from).toList());
    }
}