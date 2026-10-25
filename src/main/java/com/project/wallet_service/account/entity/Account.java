
package com.project.wallet_service.account.entity;

import com.project.wallet_service.account.enums.AccountType;
import com.project.wallet_service.common.InsufficientBalanceException;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "wallet_accounts",
        indexes = {
                @Index(
                        name = "idx_account_type_currency",
                        columnList = "account_type,currency"
                )
        }
)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ownerId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType accountType = AccountType.CUSTOMER;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "system_account_key", unique = true, length = 50)
    private String systemAccountKey;

    @Version
    private Long version;

    protected Account() {
    }

    public Account(String ownerId, String currency) {
        this(ownerId, currency, AccountType.CUSTOMER);
    }

    public Account(
            String ownerId,
            String currency,
            AccountType accountType) {

        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Owner ID must not be blank");
        }

        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Currency must be a 3-letter uppercase code");
        }

        if (accountType == null) {
            throw new IllegalArgumentException(
                    "Account type must not be null");
        }

        if (accountType == AccountType.SYSTEM) {
            this.systemAccountKey = "CLEARING:" + currency;
        }

        this.ownerId = ownerId;
        this.currency = currency;
        this.accountType = accountType;
        this.balance = BigDecimal.ZERO;
    }


    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getCurrency() {
        return currency;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Long getVersion() {
        return version;
    }

    public void credit(BigDecimal amount) {
        validateAmount(amount);
        this.balance = this.balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        validateAmount(amount);

        if (accountType == AccountType.CUSTOMER
                && this.balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException();
        }

        this.balance = this.balance.subtract(amount);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive");
        }
    }
}
