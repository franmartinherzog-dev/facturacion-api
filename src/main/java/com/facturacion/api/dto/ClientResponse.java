package com.facturacion.api.dto;

import com.facturacion.api.model.Client;

public record ClientResponse(
        Long id,
        String name,
        String taxId,
        String address,
        String email,
        String phone
) {

    public static ClientResponse from(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getTaxId(),
                client.getAddress(),
                client.getEmail(),
                client.getPhone());
    }
}