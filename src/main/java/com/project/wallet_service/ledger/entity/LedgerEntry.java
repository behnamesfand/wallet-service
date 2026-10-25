package com.project.wallet_service.ledger.entity;


import com.project.wallet_service.account.entity.Account;
import com.project.wallet_service.ledger.enums.LedgerEntryType;
import com.project.wallet_service.transaction.entity.WalletTransaction;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private WalletTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LedgerEntryType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(
            Account account,
            WalletTransaction transaction,
            LedgerEntryType type,
            BigDecimal amount) {

        if (account == null) {
            throw new IllegalArgumentException(
                    "Account must not be null");
        }

        if (transaction == null) {
            throw new IllegalArgumentException(
                    "Transaction must not be null");
        }

        if (type == null) {
            throw new IllegalArgumentException(
                    "Ledger entry type must not be null");
        }

        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Ledger entry amount must be positive");
        }

        if (amount.scale() > 4) {
            throw new IllegalArgumentException(
                    "Ledger entry amount scale must not exceed 4");
        }

        if (amount.compareTo(
                new BigDecimal("999999999999999.9999")) > 0) {
            throw new IllegalArgumentException(
                    "Ledger entry amount exceeds maximum supported value");
        }

        this.account = account;
        this.transaction = transaction;
        this.type = type;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public WalletTransaction getTransaction() {
        return transaction;
    }

    public LedgerEntryType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
