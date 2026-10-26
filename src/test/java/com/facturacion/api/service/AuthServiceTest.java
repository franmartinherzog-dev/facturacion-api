package com.facturacion.api.service;

import com.facturacion.api.dto.AuthResponse;
import com.facturacion.api.dto.LoginRequest;
import com.facturacion.api.dto.RegisterRequest;
import com.facturacion.api.exception.InvalidCredentialsException;
import com.facturacion.api.exception.UsernameAlreadyExistsException;
import com.facturacion.api.model.User;
import com.facturacion.api.repository.UserRepository;
import com.facturacion.api.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // Key used only for these tests; it is not the real application secret
    private static final String KEY = "dGVzdC1rZXktb25seS1mb3ItdW5pdC10ZXN0cy0wMTIzNDU2Nzg5LWFiY2RlZg==";

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(KEY, 3_600_000);
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerSavesTheUserWithAHashedPassword() {
        RegisterRequest request = new RegisterRequest(
                "maria", "secret123", "Maria SL", "B12345678", "Calle Mayor 1");
        when(userRepository.existsByUsername("maria")).thenReturn(false);

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("maria", saved.getUsername());
        assertNotEquals("secret123", saved.getPassword());
        assertTrue(passwordEncoder.matches("secret123", saved.getPassword()));
    }

    @Test
    void registerRejectsAnExistingUsername() {
        RegisterRequest request = new RegisterRequest(
                "maria", "secret123", "Maria SL", "B12345678", "Calle Mayor 1");
        when(userRepository.existsByUsername("maria")).thenReturn(true);

        assertThrows(UsernameAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithValidCredentialsReturnsAValidToken() {
        User user = new User("maria", passwordEncoder.encode("secret123"),
                "Maria SL", "B12345678", "Calle Mayor 1");
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(new LoginRequest("maria", "secret123"));

        assertEquals("maria", response.username());
        assertTrue(jwtService.validateToken(response.token()));
        assertEquals("maria", jwtService.extractUsername(response.token()));
    }

    @Test
    void loginWithAWrongPasswordIsRejected() {
        User user = new User("maria", passwordEncoder.encode("secret123"),
                "Maria SL", "B12345678", "Calle Mayor 1");
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(user));

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("maria", "wrong-password")));
    }

    @Test
    void loginWithAnUnknownUserIsRejected() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("nobody", "secret123")));
    }

    @Test
    void unknownUserAndWrongPasswordGiveTheSameMessage() {
        User user = new User("maria", passwordEncoder.encode("secret123"),
                "Maria SL", "B12345678", "Calle Mayor 1");
        when(userRepository.findByUsername("maria")).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        Exception wrongPassword = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("maria", "wrong-password")));
        Exception unknownUser = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("nobody", "secret123")));

        assertEquals(wrongPassword.getMessage(), unknownUser.getMessage());
    }
}