package com.facturacion.api.exception;

public class ClientNotFoundException extends RuntimeException {

    public ClientNotFoundException() {
        super("Cliente no encontrado");
    }
}