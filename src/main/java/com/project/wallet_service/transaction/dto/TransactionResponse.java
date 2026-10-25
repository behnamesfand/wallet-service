package com.project.wallet_service.transaction.dto;

import com.project.wallet_service.transaction.entity.WalletTransaction;
import com.project.wallet_service.transaction.enums.TransactionStatus;
import com.project.wallet_service.transaction.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long transactionId,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String currency,
        Long sourceAccountId,
        Long targetAccountId,
        Instant createdAt
) {

    public static TransactionResponse from(WalletTransaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getType(),
                tx.getStatus(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getSourceAccount() == null
                        ? null
                        : tx.getSourceAccount().getId(),
                tx.getTargetAccount() == null
                        ? null
                        : tx.getTargetAccount().getId(),
                tx.getCreatedAt()
        );
    }
}