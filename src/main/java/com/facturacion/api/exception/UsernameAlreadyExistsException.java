package com.facturacion.api.exception;

public class UsernameAlreadyExistsException extends RuntimeException {

    public UsernameAlreadyExistsException() {
        super("El nombre de usuario ya está en uso");
    }
}