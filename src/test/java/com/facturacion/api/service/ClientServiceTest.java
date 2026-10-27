package com.facturacion.api.service;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facturacion.api.dto.ClientRequest;
import com.facturacion.api.dto.ClientResponse;
import com.facturacion.api.exception.ClientHasInvoicesException;
import com.facturacion.api.exception.ClientNotFoundException;
import com.facturacion.api.model.Client;
import com.facturacion.api.model.User;
import com.facturacion.api.repository.ClientRepository;
import com.facturacion.api.repository.InvoiceRepository;
import com.facturacion.api.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    private ClientService clientService;
    private User maria;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(clientRepository, userRepository, invoiceRepository);
        maria = new User("maria", "hash", "Maria SL", "B12345678", "Calle Mayor 1");
    }

    private ClientRequest request(String name) {
        return new ClientRequest(name, "B76543210", "Calle Sol 5", "c@example.com", "600123456");
    }

    private Client clientOf(User owner, String name) {
        return new Client(name, "B76543210", "Calle Sol 5", "c@example.com", "600123456", owner);
    }

    @Test
    void createAssignsTheClientToTheAuthenticatedUser() {
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(clientRepository.save(any(Client.class))).thenAnswer(call -> call.getArgument(0));

        ClientResponse response = clientService.create("maria", request("Acme SL"));

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        assertSame(maria, captor.getValue().getUser());
        assertEquals("Acme SL", response.name());
    }

    @Test
    void listWithoutSearchReturnsTheClientsOfTheUser() {
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(clientRepository.findByUser(maria))
                .thenReturn(List.of(clientOf(maria, "Acme SL"), clientOf(maria, "Beta SL")));

        List<ClientResponse> result = clientService.list("maria", null);

        assertEquals(2, result.size());
        verify(clientRepository, never()).findByUserAndNameContainingIgnoreCase(any(), any());
    }

    @Test
    void listWithSearchFiltersByNameUsingTheTrimmedText() {
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(clientRepository.findByUserAndNameContainingIgnoreCase(maria, "acme"))
                .thenReturn(List.of(clientOf(maria, "Acme SL")));

        List<ClientResponse> result = clientService.list("maria", "  acme  ");

        assertEquals(1, result.size());
        assertEquals("Acme SL", result.get(0).name());
    }

    @Test
    void getReturnsAnOwnClient() {
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(clientRepository.findByIdAndUser(5L, maria))
                .thenReturn(Optional.of(clientOf(maria, "Acme SL")));

        assertEquals("Acme SL", clientService.get("maria", 5L).name());
    }

    @Test
    void getRejectsAClientThatDoesNotBelongToTheUser() {
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(clientRepository.findByIdAndUser(5L, maria)).thenReturn(Optional.empty());

        assertThrows(ClientNotFoundException.class, () -> clientService.get("maria", 5L));
        // The lookup must always be made with the authenticated user, never by id alone
        verify(clientRepository).findByIdAndUser(5L, maria);
    }

    @Test
    void updateChangesTheFieldsOfAnOwnClient() {
        Client client = clientOf(maria, "Old SL");
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(clientRepository.findByIdAndUser(5L, maria)).thenReturn(Optional.of(client));

        ClientResponse response = clientService.update("maria", 5L, request("New SL"));

        assertEquals("New SL", response.name());
        assertEquals("New SL", client.getName());
    }

    @Test
    void updateRejectsAClientThatDoesNotBelongToTheUser() {
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(clientRepository.findByIdAndUser(5L, maria)).thenReturn(Optional.empty());

        assertThrows(ClientNotFoundException.class,
                () -> clientService.update("maria", 5L, request("New SL")));
    }

    @Test
    void deleteRemovesAnOwnClient() {
        Client client = clientOf(maria, "Acme SL");
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(clientRepository.findByIdAndUser(5L, maria)).thenReturn(Optional.of(client));

        clientService.delete("maria", 5L);

        verify(clientRepository).delete(client);
    }

    @Test
    void deleteRejectsAClientThatHasInvoicesAndDeletesNothing() {
        Client client = clientOf(maria, "Acme SL");
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(clientRepository.findByIdAndUser(5L, maria)).thenReturn(Optional.of(client));
        when(invoiceRepository.existsByClient(client)).thenReturn(true);

        assertThrows(ClientHasInvoicesException.class, () -> clientService.delete("maria", 5L));
        verify(clientRepository, never()).delete(any());
    }

    @Test
    void deleteRejectsAClientThatDoesNotBelongToTheUserAndDeletesNothing() {
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(maria));
        when(clientRepository.findByIdAndUser(5L, maria)).thenReturn(Optional.empty());

        assertThrows(ClientNotFoundException.class, () -> clientService.delete("maria", 5L));
        verify(clientRepository, never()).delete(any());
    }

    @Test
    void anUnknownUsernameIsRejected() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(ClientNotFoundException.class, () -> clientService.list("ghost", null));
    }
}