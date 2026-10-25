package com.project.wallet_service.transaction.service;

import com.project.wallet_service.TestSecurityConfig;
import com.project.wallet_service.account.repository.AccountRepository;
import com.project.wallet_service.ledger.repository.LedgerEntryRepository;
import com.project.wallet_service.transaction.repository.WalletTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Import(TestSecurityConfig.class)
class WalletServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    void shouldRejectInvalidAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> walletService.deposit(
                        1L,
                        "user-1",
                        BigDecimal.ZERO,
                        "deposit-001"));

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(ledgerRepository);
    }
}
