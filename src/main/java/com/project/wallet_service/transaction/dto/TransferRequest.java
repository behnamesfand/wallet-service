package com.project.wallet_service.transaction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull @Positive Long sourceAccountId,
        @NotNull @Positive Long targetAccountId,
        @NotNull @Positive BigDecimal amount
) {
}