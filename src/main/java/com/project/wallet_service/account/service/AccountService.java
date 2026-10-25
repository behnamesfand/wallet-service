package com.project.wallet_service.account.service;

import com.project.wallet_service.account.entity.Account;
import com.project.wallet_service.account.repository.AccountRepository;
import com.project.wallet_service.common.ResourceNotFoundException;
import com.project.wallet_service.currency.CurrencyPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CurrencyPolicy currencyPolicy;

    public AccountService(AccountRepository accountRepository, CurrencyPolicy currencyPolicy) {
        this.accountRepository = accountRepository;
        this.currencyPolicy = currencyPolicy;
    }

    @Transactional
    public Long createAccount(String ownerId, String currency) {
        validateOwnerId(ownerId);
        currencyPolicy.validateSupportedCurrency(currency);

        Account account = new Account(ownerId, currency);

        return accountRepository.save(account).getId();
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long accountId, String ownerId) {
        validateAccountId(accountId);
        validateOwnerId(ownerId);

        return accountRepository
                .findByIdAndOwnerId(accountId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + accountId
                ));
    }

    @Transactional(readOnly = true)
    public List<Account> getAccountsByOwner(String ownerId) {
        validateOwnerId(ownerId);

        return accountRepository.findByOwnerId(ownerId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long accountId, String ownerId) {
        return getAccount(accountId,ownerId).getBalance();
    }

    private void validateOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Owner ID is required"
            );
        }
    }

    private void validateAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException(
                    "Account ID must be positive"
            );
        }
    }
}
