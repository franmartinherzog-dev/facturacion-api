package com.facturacion.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientRequest(

        @NotBlank
        @Size(max = 150)
        String name,

        @NotBlank
        @Pattern(regexp = "^([0-9]{8}[A-Za-z]|[XYZxyz][0-9]{7}[A-Za-z]|[A-Za-z][0-9]{7}[0-9A-Za-z])$",
                 message = "Invalid NIF/CIF format")
        String taxId,

        @Size(max = 255)
        String address,

        @Email
        @Size(max = 100)
        String email,

        @Size(max = 20)
        String phone
) { }