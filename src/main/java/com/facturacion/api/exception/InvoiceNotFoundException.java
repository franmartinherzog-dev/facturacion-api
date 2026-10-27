package com.facturacion.api.exception;

public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException() {
        super("Factura no encontrada");
    }
}