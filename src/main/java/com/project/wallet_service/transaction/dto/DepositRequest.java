package com.project.wallet_service.transaction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DepositRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull @Positive Long accountId
) {
}