
package com.project.wallet_service.account.service;

import com.project.wallet_service.account.entity.Account;
import com.project.wallet_service.account.enums.AccountType;
import com.project.wallet_service.account.repository.AccountRepository;
import com.project.wallet_service.currency.CurrencyPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAccountService {

    private static final String CLEARING_OWNER_ID = "EXTERNAL_CLEARING";
    private static final String CURRENCY = "IRR";

    private final AccountRepository accountRepository;
    private final CurrencyPolicy currencyPolicy;

    public SystemAccountService(AccountRepository accountRepository, CurrencyPolicy currencyPolicy) {
        this.accountRepository = accountRepository;
        this.currencyPolicy = currencyPolicy;
    }

    @Transactional
    public Account getClearingAccountForUpdate(String currency) {
        currencyPolicy.validateSupportedCurrency(currency);

        return accountRepository
                .findByAccountTypeAndCurrencyForUpdate(
                        AccountType.SYSTEM, currency)
                .orElseThrow(() -> new IllegalStateException(
                        "Clearing account was not initialized"));
    }

    @Transactional
    public void initializeClearingAccount() {
        accountRepository
                .findByAccountTypeAndCurrency(AccountType.SYSTEM, CURRENCY)
                .orElseGet(() -> accountRepository.save(
                        new Account(
                                CLEARING_OWNER_ID,
                                CURRENCY,
                                AccountType.SYSTEM
                        )
                ));
    }
}
