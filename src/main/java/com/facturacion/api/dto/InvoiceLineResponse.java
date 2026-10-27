package com.facturacion.api.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.facturacion.api.model.InvoiceLine;

public record InvoiceLineResponse(
        Long id,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {

    public static InvoiceLineResponse from(InvoiceLine line) {
        return new InvoiceLineResponse(
                line.getId(),
                line.getDescription(),
                line.getQuantity(),
                line.getUnitPrice(),
                // Only for display: rounded to 2 decimals. The invoice totals
                // are calculated from the unrounded subtotals.
                line.getSubtotal().setScale(2, RoundingMode.HALF_UP));
    }
}