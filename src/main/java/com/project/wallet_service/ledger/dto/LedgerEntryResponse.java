package com.project.wallet_service.ledger.dto;

import com.project.wallet_service.ledger.entity.LedgerEntry;
import com.project.wallet_service.ledger.enums.LedgerEntryType;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryResponse(
        Long entryId,
        Long accountId,
        Long transactionId,
        LedgerEntryType type,
        BigDecimal amount,
        Instant createdAt
) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getAccount().getId(),
                entry.getTransaction().getId(),
                entry.getType(),
                entry.getAmount(),
                entry.getCreatedAt()
        );
    }
}