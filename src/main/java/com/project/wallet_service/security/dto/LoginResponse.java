package com.project.wallet_service.security.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}