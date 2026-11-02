package com.facturacion.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.facturacion.api.dto.AuthResponse;
import com.facturacion.api.dto.LoginRequest;
import com.facturacion.api.dto.RegisterRequest;
import com.facturacion.api.service.AuthService;

import jakarta.validation.Valid;

/**
 * REST endpoints for registration and login (public, no token needed).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/register: creates a user. 201 if correct, 400 if the data is not valid,
     * 409 if the username already exists.
     *
     * @param request registration data
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    /**
     * POST /api/auth/login: 200 with the JWT, 401 if the credentials are wrong.
     *
     * @param request username and password
     * @return the token and the username
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}