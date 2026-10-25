package com.project.wallet_service.security.controller;

import com.project.wallet_service.security.JwtService;
import com.project.wallet_service.security.dto.LoginRequest;
import com.project.wallet_service.security.dto.LoginResponse;
import com.project.wallet_service.security.dto.RegisterRequest;
import com.project.wallet_service.security.dto.RegisterResponse;
import com.project.wallet_service.security.entity.User;
import com.project.wallet_service.security.repository.UserRepository;
import com.project.wallet_service.security.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final long expirationMs;

    public AuthController(
            UserService userService,
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            @Value("${jwt.expiration-ms}") long expirationMs) {

        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.expirationMs = expirationMs;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(
            @Valid @RequestBody RegisterRequest request) {

        Long userId = userService.register(
                request.username(),
                request.password());

        return new RegisterResponse(userId);
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()));

        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()));

        String userId = authentication.getName();
        String token = jwtService.generateToken(userId);

        return new LoginResponse(
                token,
                "Bearer",
                expirationMs / 1000);
    }
}