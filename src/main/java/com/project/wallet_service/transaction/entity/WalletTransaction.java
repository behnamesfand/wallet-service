package com.project.wallet_service.transaction.entity;

import com.project.wallet_service.account.entity.Account;
import com.project.wallet_service.transaction.enums.TransactionStatus;
import com.project.wallet_service.transaction.enums.TransactionType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "wallet_transactions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_wallet_transaction_idempotency",
        columnNames = "idempotency_key"
    )
)
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
        name = "idempotency_key",
        nullable = false,
        updatable = false,
        length = 100
    )
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_account_id")
    private Account targetAccount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected WalletTransaction() {
    }

    public WalletTransaction(
            String idempotencyKey,
            TransactionType type,
            BigDecimal amount,
            String currency,
            Account sourceAccount,
            Account targetAccount) {

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency key must contain 1 to 100 characters");
        }

        if (type == null) {
            throw new IllegalArgumentException(
                    "Transaction type must not be null");
        }

        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive");
        }

        if (amount.scale() > 4) {
            throw new IllegalArgumentException(
                    "Amount scale must not exceed 4 decimal places");
        }

        if (amount.compareTo(
                new BigDecimal("999999999999999.9999")) > 0) {
            throw new IllegalArgumentException(
                    "Amount exceeds the maximum supported value");
        }

        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Currency must be a 3-letter uppercase code");
        }

        if (sourceAccount == null || targetAccount == null) {
            throw new IllegalArgumentException(
                    "Source and target accounts must not be null");
        }

        this.idempotencyKey = idempotencyKey;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.sourceAccount = sourceAccount;
        this.targetAccount = targetAccount;
        this.status = TransactionStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void complete() {
        if (this.status != TransactionStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending transactions can be completed");
        }

        this.status = TransactionStatus.COMPLETED;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Account getSourceAccount() {
        return sourceAccount;
    }

    public Account getTargetAccount() {
        return targetAccount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
