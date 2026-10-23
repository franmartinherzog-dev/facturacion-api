package com.facturacion.api.security;

import com.facturacion.api.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    // Keys used only for these tests; they are not the real application secret
    private static final String KEY = "dGVzdC1rZXktb25seS1mb3ItdW5pdC10ZXN0cy0wMTIzNDU2Nzg5LWFiY2RlZg==";
    private static final String OTHER_KEY = "YW5vdGhlci1kaWZmZXJlbnQta2V5LWZvci11bml0LXRlc3RzLTk4NzY1NDMyMTA=";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(KEY, 3_600_000);
        user = new User("fran", "hash", "fran SL", "F12345678", "Calle MiCalle 94");
    }

    @Test
    void validTokenIsAcceptedAndContainsTheUsername() {
        String token = jwtService.generateToken(user);

        assertTrue(jwtService.validateToken(token));
        assertEquals("fran", jwtService.extractUsername(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken(user);
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        char changed = first == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + changed + parts[2].substring(1);

        assertFalse(jwtService.validateToken(tampered));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(KEY, -1000);
        String token = shortLived.generateToken(user);

        assertFalse(shortLived.validateToken(token));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreignToken = new JwtService(OTHER_KEY, 3_600_000).generateToken(user);

        assertFalse(jwtService.validateToken(foreignToken));
    }

    @Test
    void garbageIsRejected() {
        assertFalse(jwtService.validateToken("this-is-not-a-token"));
    }
}