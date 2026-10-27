package com.facturacion.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record InvoiceRequest(

        @NotNull
        Long clientId,

        @NotNull
        LocalDate issueDate,

        // Percentage, e.g. 21.00 (column numeric(5,2))
        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal vatRate,

        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal irpfRate,

        // @Valid makes Spring also validate every line inside the list
        @NotEmpty
        @Valid
        List<InvoiceLineRequest> lines
) { }