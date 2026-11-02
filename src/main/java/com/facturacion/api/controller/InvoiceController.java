package com.facturacion.api.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import com.facturacion.api.service.PdfService;

import jakarta.validation.Valid;

/**
 * REST endpoints for the invoices of the authenticated user (/api/invoices).
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final PdfService pdfService;

    public InvoiceController(InvoiceService invoiceService, PdfService pdfService) {
        this.invoiceService = invoiceService;
        this.pdfService = pdfService;
    }

    /**
     * GET /api/invoices: history of the user, newest first.
     *
     * @param principal authenticated user
     */
    @GetMapping
    public List<InvoiceResponse> list(Principal principal) {
        return invoiceService.list(principal.getName());
    }

    /**
     * GET /api/invoices/{id}: 200, or 404 if it does not exist or is not the user's.
     *
     * @param principal authenticated user
     * @param id invoice identifier
     */
    @GetMapping("/{id}")
    public InvoiceResponse get(Principal principal, @PathVariable Long id) {
        return invoiceService.get(principal.getName(), id);
    }

    /**
     * POST /api/invoices: 201 with the invoice and its calculated amounts; 400 if the data is not
     * valid (for example, no lines); 404 if the client does not exist.
     *
     * @param principal authenticated user
     * @param request client, date, tax rates and lines
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(Principal principal, @Valid @RequestBody InvoiceRequest request) {
        return invoiceService.create(principal.getName(), request);
    }

    /**
     * GET /api/invoices/{id}/pdf: the invoice as application/pdf, with the file name
     * factura-YYYY-NNNN.pdf; 404 if it does not exist or is not the user's.
     *
     * @param principal authenticated user
     * @param id invoice identifier
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(Principal principal, @PathVariable Long id) {
        // get() also checks that the invoice belongs to the user (404 otherwise)
        String number = invoiceService.get(principal.getName(), id).number();
        byte[] content = pdfService.generate(principal.getName(), id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("factura-" + number + ".pdf").build().toString())
                .body(content);
    }
}