package com.facturacion.api.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facturacion.api.dto.AuthResponse;
import com.facturacion.api.dto.LoginRequest;
import com.facturacion.api.dto.RegisterRequest;
import com.facturacion.api.exception.InvalidCredentialsException;
import com.facturacion.api.exception.UsernameAlreadyExistsException;
import com.facturacion.api.model.User;
import com.facturacion.api.repository.UserRepository;
import com.facturacion.api.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException();
        }
        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.companyName(),
                request.taxId(),
                request.address());
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        return new AuthResponse(jwtService.generateToken(user), user.getUsername());
    }
}