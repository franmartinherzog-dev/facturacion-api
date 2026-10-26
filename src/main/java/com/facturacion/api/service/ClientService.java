package com.facturacion.api.service;

import com.facturacion.api.dto.ClientRequest;
import com.facturacion.api.dto.ClientResponse;
import com.facturacion.api.exception.ClientNotFoundException;
import com.facturacion.api.model.Client;
import com.facturacion.api.model.User;
import com.facturacion.api.repository.ClientRepository;
import com.facturacion.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

    public ClientService(ClientRepository clientRepository, UserRepository userRepository) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> list(String username, String search) {
        User user = findUser(username);
        List<Client> clients = (search == null || search.isBlank())
                ? clientRepository.findByUser(user)
                : clientRepository.findByUserAndNameContainingIgnoreCase(user, search.trim());
        return clients.stream().map(ClientResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse get(String username, Long id) {
        return ClientResponse.from(findOwnedClient(username, id));
    }

    @Transactional
    public ClientResponse create(String username, ClientRequest request) {
        User user = findUser(username);
        Client client = new Client(request.name(), request.taxId(), request.address(),
                request.email(), request.phone(), user);
        return ClientResponse.from(clientRepository.save(client));
    }

    @Transactional
    public ClientResponse update(String username, Long id, ClientRequest request) {
        Client client = findOwnedClient(username, id);
        client.setName(request.name());
        client.setTaxId(request.taxId());
        client.setAddress(request.address());
        client.setEmail(request.email());
        client.setPhone(request.phone());
        return ClientResponse.from(client);
    }

    @Transactional
    public void delete(String username, Long id) {
        clientRepository.delete(findOwnedClient(username, id));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(ClientNotFoundException::new);
    }

    private Client findOwnedClient(String username, Long id) {
        return clientRepository.findByIdAndUser(id, findUser(username))
                .orElseThrow(ClientNotFoundException::new);
    }
}