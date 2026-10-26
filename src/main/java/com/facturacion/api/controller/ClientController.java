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

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    public List<ClientResponse> list(Principal principal,
                                     @RequestParam(required = false) String search) {
        return clientService.list(principal.getName(), search);
    }

    @GetMapping("/{id}")
    public ClientResponse get(Principal principal, @PathVariable Long id) {
        return clientService.get(principal.getName(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse create(Principal principal, @Valid @RequestBody ClientRequest request) {
        return clientService.create(principal.getName(), request);
    }

    @PutMapping("/{id}")
    public ClientResponse update(Principal principal, @PathVariable Long id,
                                 @Valid @RequestBody ClientRequest request) {
        return clientService.update(principal.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal principal, @PathVariable Long id) {
        clientService.delete(principal.getName(), id);
    }
}