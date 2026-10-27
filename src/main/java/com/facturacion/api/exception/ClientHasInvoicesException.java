package com.facturacion.api.exception;

public class ClientHasInvoicesException extends RuntimeException {

    public ClientHasInvoicesException() {
        super("No se puede eliminar un cliente que tiene facturas");
    }
}