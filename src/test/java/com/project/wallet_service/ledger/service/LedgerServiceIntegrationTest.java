
package com.project.wallet_service.ledger.service;

import com.project.wallet_service.TestSecurityConfig;
import com.project.wallet_service.account.entity.Account;
import com.project.wallet_service.account.repository.AccountRepository;
import com.project.wallet_service.account.service.SystemAccountService;
import com.project.wallet_service.common.ResourceNotFoundException;
import com.project.wallet_service.ledger.dto.LedgerEntryResponse;
import com.project.wallet_service.ledger.dto.WalletTransactionResponse;
import com.project.wallet_service.ledger.repository.LedgerEntryRepository;
import com.project.wallet_service.transaction.repository.WalletTransactionRepository;
import com.project.wallet_service.transaction.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ledger-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=wallet",
        "spring.datasource.password=wallet",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(TestSecurityConfig.class)
class LedgerServiceIntegrationTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository ledgerRepository;

    @Autowired
    private WalletTransactionRepository transactionRepository;

    @Autowired
    private SystemAccountService systemAccountService;

    @BeforeEach
    void cleanDatabase() {
        ledgerRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        systemAccountService.initializeClearingAccount();
    }

    private Account createAccount(String owner, String currency) {
        return accountRepository.saveAndFlush(
                new Account(owner, currency));
    }

    @Test
    void shouldReturnAccountHistory() {
        Account account = createAccount("user-1", "IRR");

        walletService.deposit(
                account.getId(),
                "user-1",
                new BigDecimal("500"),
                "ledger-history-deposit");

        walletService.withdraw(
                account.getId(),
                "user-1",
                new BigDecimal("100"),
                "ledger-history-withdraw");

        Page<LedgerEntryResponse> page =
                ledgerService.getAccountHistory(
                        account.getId(),
                        "user-1",
                        PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getContent().size());

        assertTrue(page.getContent().stream()
                .allMatch(entry ->
                        entry.accountId().equals(account.getId())));
    }

    @Test
    void shouldApplyPaginationToAccountHistory() {
        Account account = createAccount("user-1", "IRR");

        walletService.deposit(
                account.getId(),
                "user-1",
                new BigDecimal("100"),
                "page-deposit-1");

        walletService.deposit(
                account.getId(),
                "user-1",
                new BigDecimal("200"),
                "page-deposit-2");

        walletService.deposit(
                account.getId(),
                "user-1",
                new BigDecimal("300"),
                "page-deposit-3");

        Page<LedgerEntryResponse> firstPage =
                ledgerService.getAccountHistory(
                        account.getId(),
                        "user-1",
                        PageRequest.of(0, 2));

        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getContent().size());
        assertTrue(firstPage.hasNext());

        Page<LedgerEntryResponse> secondPage =
                ledgerService.getAccountHistory(
                        account.getId(),
                        "user-1",
                        PageRequest.of(1, 2));

        assertEquals(1, secondPage.getContent().size());
        assertFalse(secondPage.hasNext());
    }

    @Test
    void shouldReturnTransactionDetails() {
        Account account = createAccount("user-1", "IRR");

        Long transactionId = walletService.deposit(
                account.getId(),
                "user-1",
                new BigDecimal("250"),
                "transaction-details-deposit");

        WalletTransactionResponse response =
                ledgerService.getTransactionDetails(transactionId, "user-1");

        assertEquals(transactionId, response.id());
        assertEquals("DEPOSIT", response.type().name());
        assertEquals("COMPLETED", response.status().name());
        assertEquals("IRR", response.currency());
        assertEquals(
                0,
                response.amount().compareTo(new BigDecimal("250")));

        assertNull(response.sourceAccountId());
        assertEquals(account.getId(), response.targetAccountId());
    }

    @Test
    void shouldReturnLedgerEntriesForTransaction() {
        Account source = createAccount("user-1", "IRR");
        Account target = createAccount("user-2", "IRR");

        walletService.deposit(
                source.getId(),
                "user-1",
                new BigDecimal("500"),
                "transaction-ledger-initial");

        Long transactionId = walletService.transfer(
                source.getId(),
                target.getId(),
                "user-1",
                new BigDecimal("150"),
                "transaction-ledger-transfer");

        List<LedgerEntryResponse> entries =
                ledgerService.getTransactionLedger(transactionId, "user-1");

        assertEquals(2, entries.size());

        assertTrue(entries.stream()
                .allMatch(entry ->
                        entry.transactionId().equals(transactionId)));

        assertEquals(
                0,
                entries.stream()
                        .map(LedgerEntryResponse::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .compareTo(new BigDecimal("300")));
    }

    @Test
    void shouldRejectHistoryForMissingAccount() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> ledgerService.getAccountHistory(
                        Long.MAX_VALUE,
                        "user-1",
                        PageRequest.of(0, 10)));
    }

    @Test
    void shouldRejectDetailsForMissingTransaction() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> ledgerService.getTransactionDetails(Long.MAX_VALUE, "user-1"));
    }

    @Test
    void shouldRejectLedgerLookupForMissingTransaction() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> ledgerService.getTransactionLedger(Long.MAX_VALUE, "user-1"));
    }

    @Test
    void shouldRejectAccountHistoryForAnotherUser() {
        Account account = createAccount("user-1", "IRR");

        walletService.deposit(
                account.getId(),
                "user-1",
                new BigDecimal("100"),
                "ownership-history-deposit");

        assertThrows(
                ResourceNotFoundException.class,
                () -> ledgerService.getAccountHistory(
                        account.getId(),
                        "user-2",
                        PageRequest.of(0, 10))
        );
    }

    @Test
    void shouldRejectTransactionDetailsForUnrelatedUser() {
        Account source = createAccount("user-1", "IRR");
        Account target = createAccount("user-2", "IRR");

        walletService.deposit(
                source.getId(),
                "user-1",
                new BigDecimal("300"),
                "ownership-details-deposit");

        Long transactionId = walletService.transfer(
                source.getId(),
                target.getId(),
                "user-1",
                new BigDecimal("100"),
                "ownership-details-transfer");

        assertThrows(
                ResourceNotFoundException.class,
                () -> ledgerService.getTransactionDetails(
                        transactionId, "user-3")
        );
    }

    @Test
    void shouldAllowTargetOwnerToViewTransferDetails() {
        Account source = createAccount("user-1", "IRR");
        Account target = createAccount("user-2", "IRR");

        walletService.deposit(
                source.getId(),
                "user-1",
                new BigDecimal("300"),
                "ownership-target-deposit");

        Long transactionId = walletService.transfer(
                source.getId(),
                target.getId(),
                "user-1",
                new BigDecimal("100"),
                "ownership-target-transfer");

        WalletTransactionResponse response =
                ledgerService.getTransactionDetails(
                        transactionId, "user-2");

        assertEquals(transactionId, response.id());
        assertEquals(target.getId(), response.targetAccountId());
    }

    @Test
    void shouldRejectTransactionLedgerForUnrelatedUser() {
        Account source = createAccount("user-1", "IRR");
        Account target = createAccount("user-2", "IRR");

        walletService.deposit(
                source.getId(),
                "user-1",
                new BigDecimal("300"),
                "ownership-ledger-deposit");

        Long transactionId = walletService.transfer(
                source.getId(),
                target.getId(),
                "user-1",
                new BigDecimal("100"),
                "ownership-ledger-transfer");

        assertThrows(
                ResourceNotFoundException.class,
                () -> ledgerService.getTransactionLedger(
                        transactionId, "user-3")
        );
    }
}
