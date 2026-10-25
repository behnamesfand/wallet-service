
package com.project.wallet_service.account.service;

import com.project.wallet_service.TestSecurityConfig;
import com.project.wallet_service.account.entity.Account;
import com.project.wallet_service.account.repository.AccountRepository;
import com.project.wallet_service.common.ResourceNotFoundException;
import com.project.wallet_service.currency.CurrencyPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Import(TestSecurityConfig.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                accountRepository,
                new CurrencyPolicy()
        );
    }

    @Test
    void shouldCreateAccount() {
        Account savedAccount = mock(Account.class);
        when(savedAccount.getId()).thenReturn(1L);

        when(accountRepository.save(any(Account.class)))
                .thenReturn(savedAccount);

        Long accountId = accountService.createAccount("user-1", "IRR");

        assertEquals(1L, accountId);

        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void shouldRejectBlankOwnerId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount(" ", "IRR"));

        verifyNoInteractions(accountRepository);
    }

    @Test
    void shouldRejectInvalidCurrency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount("user-1", "irr"));

        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount("user-1", "US"));

        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount("user-1", null));

        verifyNoInteractions(accountRepository);
    }

    @Test
    void shouldGetExistingAccount() {
        Account account = new Account("user-1", "IRR");

        when(accountRepository.findByIdAndOwnerId(1L, "user-1"))
                .thenReturn(Optional.of(account));

        Account result = accountService.getAccount(1L, "user-1");

        assertSame(account, result);

        verify(accountRepository)
                .findByIdAndOwnerId(1L, "user-1");
    }

    @Test
    void shouldThrowWhenAccountDoesNotExist() {
        when(accountRepository.findByIdAndOwnerId(99L, "test"))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> accountService.getAccount(99L, "test")
        );

        verify(accountRepository)
                .findByIdAndOwnerId(99L, "test");
    }

    @Test
    void shouldRejectInvalidAccountId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccount(null,"test"));

        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccount(0L,"test"));

        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccount(-1L,"test"));

        verifyNoInteractions(accountRepository);
    }

    @Test
    void shouldReturnAccountBalance() {
        Account account = mock(Account.class);

        when(accountRepository.findByIdAndOwnerId(1L, "user-1"))
                .thenReturn(Optional.of(account));

        when(account.getBalance())
                .thenReturn(new BigDecimal("250.00"));

        BigDecimal balance = accountService.getBalance(1L, "user-1");

        assertEquals(
                0,
                balance.compareTo(new BigDecimal("250"))
        );

        verify(accountRepository)
                .findByIdAndOwnerId(1L, "user-1");
    }

    @Test
    void shouldReturnAccountsForOwner() {
        List<Account> accounts = List.of(
                new Account("user-1", "IRR"),
                new Account("user-1", "USD"));

        when(accountRepository.findByOwnerId("user-1"))
                .thenReturn(accounts);

        List<Account> result =
                accountService.getAccountsByOwner("user-1");

        assertEquals(2, result.size());
        assertSame(accounts, result);
        verify(accountRepository).findByOwnerId("user-1");
    }

    @Test
    void shouldRejectBlankOwnerWhenGettingAccounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccountsByOwner(" "));

        verifyNoInteractions(accountRepository);
    }

    @Test
    void shouldReturnEmptyListWhenOwnerHasNoAccounts() {
        when(accountRepository.findByOwnerId("user-2"))
                .thenReturn(List.of());

        List<Account> result =
                accountService.getAccountsByOwner("user-2");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnAccountWhenOwnedByUser() {
        Account account = new Account("user-1", "IRR");

        when(accountRepository.findByIdAndOwnerId(1L, "user-1"))
                .thenReturn(Optional.of(account));

        Account result = accountService.getAccount(1L, "user-1");

        assertNotNull(result);
        assertEquals("user-1", result.getOwnerId());
        assertEquals("IRR", result.getCurrency());

        verify(accountRepository).findByIdAndOwnerId(1L, "user-1");
    }

    @Test
    void shouldNotAllowAccessToAnotherUsersAccount() {
        when(accountRepository.findByIdAndOwnerId(1L, "user-2"))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> accountService.getAccount(1L, "user-2")
        );

        verify(accountRepository).findByIdAndOwnerId(1L, "user-2");
    }

    @Test
    void shouldNotReturnAnotherUsersAccount() {
        Account account = new Account("user-1", "IRR");
        account.setId(1L);

        when(accountRepository.save(any(Account.class)))
                .thenReturn(account);

        account = accountRepository.save(account);

        Long accountId = account.getId();

        assertThrows(
                ResourceNotFoundException.class,
                () -> accountService.getAccount(accountId, "user-2")
        );
    }

    @Test
    void shouldNotReturnAnotherUsersBalance() {
        Account account = new Account("user-1", "IRR");
        account.setId(1L);

        when(accountRepository.save(any(Account.class)))
                .thenReturn(account);

        account = accountRepository.save(account);

        Long accountId = account.getId();

        assertThrows(
                ResourceNotFoundException.class,
                () -> accountService.getBalance(accountId, "user-2")
        );
    }
}
