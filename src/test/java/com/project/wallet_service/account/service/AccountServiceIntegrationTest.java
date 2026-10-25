
package com.project.wallet_service.account.service;

import com.project.wallet_service.TestSecurityConfig;
import com.project.wallet_service.account.entity.Account;
import com.project.wallet_service.account.repository.AccountRepository;
import com.project.wallet_service.common.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Import(TestSecurityConfig.class)
class AccountServiceIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private SystemAccountService systemAccountService;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        systemAccountService.initializeClearingAccount();
    }

    @Test
    void shouldCreateAndPersistAccount() {
        Long accountId = accountService.createAccount("user-1", "IRR");

        Account account = accountRepository.findById(accountId)
                .orElseThrow();

        assertEquals("user-1", account.getOwnerId());
        assertEquals("IRR", account.getCurrency());
        assertEquals(0, account.getBalance().compareTo(java.math.BigDecimal.ZERO));
    }

    @Test
    void shouldGetExistingAccount() {
        Long accountId = accountService.createAccount("user-1", "IRR");

        Account account = accountService.getAccount(accountId,"user-1");

        assertEquals(accountId, account.getId());
        assertEquals("user-1", account.getOwnerId());
    }

    @Test
    void shouldThrowWhenAccountDoesNotExist() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> accountService.getAccount(999999L,"test")
        );
    }

    @Test
    void shouldReturnAccountsForOwner() {
        accountService.createAccount("user-1", "IRR");
        accountService.createAccount("user-1", "EUR");
        accountService.createAccount("user-2", "IRR");

        List<Account> accounts =
                accountService.getAccountsByOwner("user-1");

        assertEquals(2, accounts.size());
        assertTrue(accounts.stream()
                .allMatch(account ->
                        account.getOwnerId().equals("user-1")));
    }

    @Test
    void shouldReturnEmptyListWhenOwnerHasNoAccounts() {
        List<Account> accounts =
                accountService.getAccountsByOwner("user-without-accounts");

        assertTrue(accounts.isEmpty());
    }

    @Test
    void shouldReturnZeroBalanceForNewAccount() {
        Long accountId = accountService.createAccount("user-1", "IRR");

        assertEquals(
                0,
                accountService.getBalance(accountId,"user-1")
                        .compareTo(java.math.BigDecimal.ZERO)
        );
    }

    @Test
    void shouldRejectInvalidCurrency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount("user-1", "irr")
        );
    }

    @Test
    void shouldRejectBlankOwnerId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount(" ", "IRR")
        );
    }

    @Test
    void shouldRejectInvalidAccountId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccount(0L,"user-1")
        );
    }
}
