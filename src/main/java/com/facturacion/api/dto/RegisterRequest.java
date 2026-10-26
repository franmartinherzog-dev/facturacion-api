package com.facturacion.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        @Size(min = 3, max = 50)
        String username,

        // Maximum 72 because BCrypt ignores everything beyond 72 bytes
        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotBlank
        @Size(max = 150)
        String companyName,

        @NotBlank
        @Pattern(regexp = "^([0-9]{8}[A-Za-z]|[XYZxyz][0-9]{7}[A-Za-z]|[A-Za-z][0-9]{7}[0-9A-Za-z])$",
                message = "Formato de NIF/CIF no válido")
        String taxId,

        @Size(max = 255)
        String address
) {}