package com.facturacion.api.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.facturacion.api.dto.InvoiceRequest;
import com.facturacion.api.dto.InvoiceResponse;
import com.facturacion.api.service.InvoiceService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public List<InvoiceResponse> list(Principal principal) {
        return invoiceService.list(principal.getName());
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(Principal principal, @PathVariable Long id) {
        return invoiceService.get(principal.getName(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(Principal principal, @Valid @RequestBody InvoiceRequest request) {
        return invoiceService.create(principal.getName(), request);
    }
}