package com.project.wallet_service.ledger.dto;

import com.project.wallet_service.transaction.enums.TransactionStatus;
import com.project.wallet_service.transaction.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransactionResponse(
        Long id,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String currency,
        Long sourceAccountId,
        Long targetAccountId,
        Instant createdAt
) {
}