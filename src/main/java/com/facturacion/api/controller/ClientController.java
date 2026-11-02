package com.facturacion.api.controller;

import com.facturacion.api.dto.ClientRequest;
import com.facturacion.api.dto.ClientResponse;
import com.facturacion.api.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * REST endpoints for the clients of the authenticated user (/api/clients).
 */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    /**
     * GET /api/clients[?search=text]: lists the clients, optionally filtered by name.
     *
     * @param principal authenticated user
     * @param search optional text to look for in the name
     */
    @GetMapping
    public List<ClientResponse> list(Principal principal,
                                     @RequestParam(required = false) String search) {
        return clientService.list(principal.getName(), search);
    }

    /**
     * GET /api/clients/{id}: 200, or 404 if it does not exist or is not the user's.
     *
     * @param principal authenticated user
     * @param id client identifier
     */
    @GetMapping("/{id}")
    public ClientResponse get(Principal principal, @PathVariable Long id) {
        return clientService.get(principal.getName(), id);
    }

    /**
     * POST /api/clients: 201 with the created client, 400 if the data is not valid.
     *
     * @param principal authenticated user
     * @param request client data
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse create(Principal principal, @Valid @RequestBody ClientRequest request) {
        return clientService.create(principal.getName(), request);
    }

    /**
     * PUT /api/clients/{id}: 200 with the updated client, 404 or 400 as above.
     *
     * @param principal authenticated user
     * @param id client identifier
     * @param request new data
     */
    @PutMapping("/{id}")
    public ClientResponse update(Principal principal, @PathVariable Long id,
                                 @Valid @RequestBody ClientRequest request) {
        return clientService.update(principal.getName(), id, request);
    }

    /**
     * DELETE /api/clients/{id}: 204; 404 if not found; 409 if the client has invoices.
     *
     * @param principal authenticated user
     * @param id client identifier
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal principal, @PathVariable Long id) {
        clientService.delete(principal.getName(), id);
    }
}