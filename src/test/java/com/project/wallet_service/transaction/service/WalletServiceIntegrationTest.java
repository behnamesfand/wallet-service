package com.project.wallet_service.transaction.service;

import com.project.wallet_service.TestSecurityConfig;
import com.project.wallet_service.account.entity.Account;
import com.project.wallet_service.account.repository.AccountRepository;
import com.project.wallet_service.account.service.SystemAccountService;
import com.project.wallet_service.common.IdempotencyConflictException;
import com.project.wallet_service.common.ResourceNotFoundException;
import com.project.wallet_service.ledger.entity.LedgerEntry;
import com.project.wallet_service.ledger.enums.LedgerEntryType;
import com.project.wallet_service.ledger.repository.LedgerEntryRepository;
import com.project.wallet_service.transaction.entity.WalletTransaction;
import com.project.wallet_service.transaction.enums.TransactionStatus;
import com.project.wallet_service.transaction.repository.WalletTransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:wallet-test;DB_CLOSE_DELAY=-1", "spring.datasource.username=wallet", "spring.datasource.password=wallet", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(TestSecurityConfig.class)
class WalletServiceIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private WalletTransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerRepository;

    @Autowired
    private SystemAccountService systemAccountService;

    @MockitoSpyBean
    private LedgerEntryRepository ledgerRepositorySpy;

    @BeforeEach
    void cleanDatabase() {
        ledgerRepository.deleteAllInBatch();
        transactionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        systemAccountService.initializeClearingAccount();
    }

    private Account createAccount(String owner, String currency) {
        return accountRepository.saveAndFlush(new Account(owner, currency));
    }

    private BigDecimal balanceOf(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    private void assertBalance(Long accountId, String expectedBalance) {

        assertEquals(0, balanceOf(accountId).compareTo(new BigDecimal(expectedBalance)));
    }

    private void assertCustomerBalanceMatchesLedger(Long accountId) {
        List<LedgerEntry> entries = ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId);

        BigDecimal credits = entries.stream().filter(e -> e.getType() == LedgerEntryType.CREDIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal debits = entries.stream().filter(e -> e.getType() == LedgerEntryType.DEBIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ledgerBalance = credits.subtract(debits);

        assertEquals(0, balanceOf(accountId).compareTo(ledgerBalance), "Account balance does not match ledger: " + accountId);
    }

    private void assertClearingBalanceMatchesLedger() {
        Account clearingAccount = accountRepository
                .findByAccountTypeAndCurrency(
                        com.project.wallet_service.account.enums.AccountType.SYSTEM,
                        "IRR")
                .orElseThrow();

        assertCustomerBalanceMatchesLedger(clearingAccount.getId());
    }

    private void assertEveryTransactionHasTwoBalancedEntries() {
        List<WalletTransaction> transactions = transactionRepository.findAll();

        for (WalletTransaction tx : transactions) {
            List<LedgerEntry> entries = ledgerRepository.findByTransaction_IdOrderByCreatedAtAsc(tx.getId());

            assertEquals(2, entries.size(), "Every transaction must have two ledger entries");

            BigDecimal debits = entries.stream().filter(e -> e.getType() == LedgerEntryType.DEBIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal credits = entries.stream().filter(e -> e.getType() == LedgerEntryType.CREDIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            assertEquals(0, debits.compareTo(credits), "Debit and credit totals must match");

            assertEquals(
                    TransactionStatus.COMPLETED,
                    tx.getStatus(),
                    "Every persisted transaction must be completed"
            );
        }
    }

    @Test
    void shouldTransferMoneyAndCreateDoubleEntryLedger() {
        Account source = createAccount("user-1", "IRR");
        Account target = createAccount("user-2", "IRR");

        walletService.deposit(source.getId(), "user-1", new BigDecimal("1000"), "deposit-initial");

        walletService.transfer(source.getId(), target.getId(), "user-1", new BigDecimal("300"), "transfer-001");

        assertBalance(source.getId(), "700");
        assertBalance(target.getId(), "300");

        assertEquals(2, transactionRepository.count());
        assertEquals(4, ledgerRepository.count());

        assertEveryTransactionHasTwoBalancedEntries();

        assertTrue(transactionRepository.findAll().stream().allMatch(tx -> tx.getStatus() == TransactionStatus.COMPLETED));
    }

    @Test
    void shouldReturnSameTransactionForRepeatedRequest() {
        Account source = createAccount("user-1", "IRR");
        Account target = createAccount("user-2", "IRR");

        walletService.deposit(source.getId(), "user-1", new BigDecimal("500"), "initial-deposit");

        Long firstId = walletService.transfer(source.getId(), target.getId(), "user-1", new BigDecimal("100"), "transfer-repeat");

        Long secondId = walletService.transfer(source.getId(), target.getId(), "user-1", new BigDecimal("100"), "transfer-repeat");

        assertEquals(firstId, secondId);

        assertBalance(source.getId(), "400");
        assertBalance(target.getId(), "100");

        assertEquals(2, transactionRepository.count());
        assertEquals(4, ledgerRepository.count());

        assertEveryTransactionHasTwoBalancedEntries();
    }

    @Test
    void shouldRejectSameKeyWithDifferentAmount() {
        Account source = createAccount("user-1", "IRR");
        Account target = createAccount("user-2", "IRR");

        walletService.deposit(source.getId(), "user-1", new BigDecimal("500"), "initial-deposit");

        walletService.transfer(source.getId(), target.getId(), "user-1", new BigDecimal("100"), "same-key");

        assertThrows(IdempotencyConflictException.class, () -> walletService.transfer(source.getId(), target.getId(), "user-1", new BigDecimal("200"), "same-key"));

        assertBalance(source.getId(), "400");
        assertBalance(target.getId(), "100");

        assertEquals(2, transactionRepository.count());
        assertEquals(4, ledgerRepository.count());
    }

    @Test
    void shouldRollbackTransferWhenBalanceIsInsufficient() {
        Account source = createAccount("user-1", "IRR");
        Account target = createAccount("user-2", "IRR");

        assertThrows(RuntimeException.class, () -> walletService.transfer(source.getId(), target.getId(), "user-1", new BigDecimal("100"), "insufficient"));

        assertBalance(source.getId(), "0");
        assertBalance(target.getId(), "0");

        assertEquals(0, transactionRepository.count());
        assertEquals(0, ledgerRepository.count());
    }

    @Test
    void shouldHandleConcurrentRequestsWithSameKey() throws Exception {

        Account source = createAccount("user-1", "IRR");
        Account target = createAccount("user-2", "IRR");

        walletService.deposit(source.getId(), "user-1", new BigDecimal("1000"), "initial-deposit");

        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {

            Future<Long> first = executor.submit(() -> {
                start.await();

                return walletService.transfer(source.getId(), target.getId(), "user-1", new BigDecimal("100"), "concurrent-key");
            });

            Future<Long> second = executor.submit(() -> {
                start.await();

                return walletService.transfer(source.getId(), target.getId(), "user-1", new BigDecimal("100"), "concurrent-key");
            });

            start.countDown();

            Long firstId = first.get();
            Long secondId = second.get();

            assertEquals(firstId, secondId);
        }

        assertBalance(source.getId(), "900");
        assertBalance(target.getId(), "100");

        assertEquals(2, transactionRepository.count());
        assertEquals(4, ledgerRepository.count());

        assertEveryTransactionHasTwoBalancedEntries();
    }

    @Test
    void shouldWithdrawMoneyAndCreateDoubleEntryLedger() {
        Account account = createAccount("user-1", "IRR");

        walletService.deposit(account.getId(), "user-1", new BigDecimal("500"), "withdraw-initial-deposit");

        walletService.withdraw(account.getId(), "user-1", new BigDecimal("100"), "withdraw-success");

        assertBalance(account.getId(), "400");

        assertEquals(2, transactionRepository.count());
        assertEquals(4, ledgerRepository.count());

        assertEveryTransactionHasTwoBalancedEntries();
        assertCustomerBalanceMatchesLedger(account.getId());
    }

    @Test
    void shouldRollbackWithdrawalWhenBalanceIsInsufficient() {
        Account account = createAccount("user-1", "IRR");

        assertThrows(RuntimeException.class, () -> walletService.withdraw(account.getId(), "user-1", new BigDecimal("100"), "withdraw-insufficient"));

        assertBalance(account.getId(), "0");

        assertEquals(0, transactionRepository.count());
        assertEquals(0, ledgerRepository.count());
    }

    @Test
    void shouldNotWithdrawTwiceForSameIdempotencyKey() {
        Account account = createAccount("user-1", "IRR");

        walletService.deposit(account.getId(), "user-1", new BigDecimal("500"), "initial-deposit");

        Long firstId = walletService.withdraw(account.getId(), "user-1", new BigDecimal("100"), "withdraw-repeat");

        Long secondId = walletService.withdraw(account.getId(), "user-1", new BigDecimal("100"), "withdraw-repeat");

        assertEquals(firstId, secondId);

        assertBalance(account.getId(), "400");

        assertEquals(2, transactionRepository.count());
        assertEquals(4, ledgerRepository.count());

        assertEveryTransactionHasTwoBalancedEntries();
        assertCustomerBalanceMatchesLedger(account.getId());
    }

    @Test
    void shouldKeepCustomerBalanceConsistentWithLedger() {
        Account source = createAccount("user-1", "IRR");
        Account target = createAccount("user-2", "IRR");

        walletService.deposit(source.getId(), "user-1", new BigDecimal("1000"), "balance-ledger-deposit");

        walletService.transfer(source.getId(), target.getId(), "user-1", new BigDecimal("300"), "balance-ledger-transfer");

        walletService.withdraw(target.getId(), "user-2", new BigDecimal("100"), "balance-ledger-withdraw");

        assertBalance(source.getId(), "700");
        assertBalance(target.getId(), "200");

        assertCustomerBalanceMatchesLedger(source.getId());
        assertCustomerBalanceMatchesLedger(target.getId());

        assertEquals(3, transactionRepository.count());
        assertEquals(6, ledgerRepository.count());

        assertEveryTransactionHasTwoBalancedEntries();
    }

    @Test
    void shouldRejectTransferToNonExistingAccount() {
        Account source = createAccount("user-1", "IRR");

        walletService.deposit(source.getId(), "user-1", new BigDecimal("500"), "missing-target-deposit");

        assertThrows(RuntimeException.class, () -> walletService.transfer(source.getId(), Long.MAX_VALUE, "user-1", new BigDecimal("100"), "missing-target-transfer"));

        assertBalance(source.getId(), "500");
        assertEquals(1, transactionRepository.count());
        assertEquals(2, ledgerRepository.count());
    }

    @Test
    void shouldRejectTransferToSameAccount() {
        Account account = createAccount("user-1", "IRR");

        walletService.deposit(account.getId(), "user-1", new BigDecimal("500"), "same-account-deposit");

        assertThrows(IllegalArgumentException.class, () -> walletService.transfer(account.getId(), account.getId(), "user-1", new BigDecimal("100"), "same-account-transfer"));

        assertBalance(account.getId(), "500");
        assertEquals(1, transactionRepository.count());
        assertEquals(2, ledgerRepository.count());
    }

    @Test
    void shouldRejectTransferBetweenDifferentCurrencies() {
        Account irrAccount = createAccount("user-1", "IRR");
        Account usdAccount = createAccount("user-2", "USD");

        walletService.deposit(irrAccount.getId(), "user-1", new BigDecimal("500"), "currency-deposit");

        assertThrows(IllegalArgumentException.class, () -> walletService.transfer(irrAccount.getId(), usdAccount.getId(), "user-1", new BigDecimal("100"), "currency-mismatch-transfer"));

        assertBalance(irrAccount.getId(), "500");
        assertBalance(usdAccount.getId(), "0");

        assertEquals(1, transactionRepository.count());
        assertEquals(2, ledgerRepository.count());
    }

    @Test
    void shouldRejectDepositWithSameKeyAndDifferentAmount() {
        Account account = createAccount("user-1", "IRR");

        walletService.deposit(account.getId(), "user-1", new BigDecimal("500"), "deposit-same-key");

        assertThrows(IdempotencyConflictException.class, () -> walletService.deposit(account.getId(), "user-1", new BigDecimal("700"), "deposit-same-key"));

        assertBalance(account.getId(), "500");
        assertEquals(1, transactionRepository.count());
        assertEquals(2, ledgerRepository.count());
    }

    @Test
    void shouldRollbackTransferWhenLedgerPersistenceFails() {
        Account source = createAccount("user-1", "IRR");
        Account target = createAccount("user-2", "IRR");

        walletService.deposit(source.getId(), "user-1", new BigDecimal("500"), "ledger-failure-deposit");

        doThrow(new DataIntegrityViolationException("Simulated ledger persistence failure")).when(ledgerRepositorySpy).saveAll(anyList());

        assertThrows(DataIntegrityViolationException.class, () -> walletService.transfer(source.getId(), target.getId(), "user-1", new BigDecimal("100"), "ledger-failure-transfer"));

        assertBalance(source.getId(), "500");
        assertBalance(target.getId(), "0");

        assertEquals(1, transactionRepository.count());
        assertEquals(2, ledgerRepository.count());

        assertEquals(
                0,
                transactionRepository.findAll().stream()
                        .filter(tx -> tx.getStatus() != TransactionStatus.COMPLETED)
                        .count(),
                "No incomplete transaction should remain after rollback"
        );
    }

    @Test
    void shouldRejectZeroOrNegativeAmount() {
        Account account = createAccount("user-1", "IRR");

        assertThrows(IllegalArgumentException.class, () -> walletService.deposit(account.getId(), "user-1", BigDecimal.ZERO, "zero-amount"));

        assertThrows(IllegalArgumentException.class, () -> walletService.withdraw(account.getId(), "user-1", new BigDecimal("-10"), "negative-amount"));

        assertEquals(0, transactionRepository.count());
        assertEquals(0, ledgerRepository.count());
        assertBalance(account.getId(), "0");
    }

    @Test
    void shouldRejectAmountWithMoreThanFourDecimalPlaces() {
        Account account = createAccount("user-1", "IRR");

        assertThrows(IllegalArgumentException.class, () -> walletService.deposit(account.getId(), "user-1", new BigDecimal("10.12345"), "too-many-decimals"));

        assertEquals(0, transactionRepository.count());
        assertEquals(0, ledgerRepository.count());
        assertBalance(account.getId(), "0");
    }


    @Test
    void shouldRejectInvalidIdempotencyKey() {
        Account account = createAccount("user-1", "IRR");

        assertThrows(IllegalArgumentException.class, () -> walletService.deposit(account.getId(), "user-1", new BigDecimal("100"), " "));

        assertThrows(IllegalArgumentException.class, () -> walletService.deposit(account.getId(), "user-1", new BigDecimal("100"), "x".repeat(101)));

        assertEquals(0, transactionRepository.count());
        assertEquals(0, ledgerRepository.count());
    }

    @Test
    void shouldRejectInvalidAccountId() {
        assertThrows(IllegalArgumentException.class, () -> walletService.deposit(null, "user-1", new BigDecimal("100"), "null-account"));

        assertThrows(IllegalArgumentException.class, () -> walletService.withdraw(0L, "user-1", new BigDecimal("100"), "zero-account"));

        assertEquals(0, transactionRepository.count());
        assertEquals(0, ledgerRepository.count());
    }

    @Test
    void shouldAcceptMaximumSupportedAmount() {
        BigDecimal maxAmount = new BigDecimal("999999999999999.9999");

        Long accountId = createAccount("user-1", "IRR").getId();

        Long transactionId = walletService.deposit(accountId, "user-1", maxAmount, "max-amount-key");

        assertNotNull(transactionId);
        assertEquals(maxAmount, balanceOf(accountId));
    }

    @Test
    void shouldRejectAmountAboveMaximumSupportedAmount() {
        BigDecimal amount = new BigDecimal("1000000000000000.0000");

        Long accountId = createAccount("user-2", "IRR").getId();

        assertThrows(IllegalArgumentException.class, () -> walletService.deposit(accountId, "user-2", amount, "above-max-key"));

        assertEquals(0, balanceOf(accountId).intValue());
        assertEquals(0, transactionRepository.count());
        assertEquals(0, ledgerRepository.count());
    }

    @Test
    void shouldAcceptAmountWithTrailingZerosBeyondFourDecimalPlaces() {
        Long accountId = createAccount("user-3", "IRR").getId();

        assertDoesNotThrow(() -> walletService.deposit(accountId, "user-3", new BigDecimal("10.00000"), "trailing-zeros-key"));

        assertEquals(0, new BigDecimal("10.0000").compareTo(balanceOf(accountId)));
    }

    @Test
    void shouldRejectConcurrentRequestsWithSameKeyAndDifferentAmounts() throws Exception {

        Long accountId = createAccount("user-1", "IRR").getId();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<Long> first = pool.submit(() -> {
                ready.countDown();
                start.await();

                return walletService.deposit(accountId, "user-1", new BigDecimal("100"), "concurrent-conflict-key");
            });

            Future<Long> second = pool.submit(() -> {
                ready.countDown();
                start.await();

                return walletService.deposit(accountId, "user-1", new BigDecimal("200"), "concurrent-conflict-key");
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successCount = 0;
            int conflictCount = 0;

            for (Future<Long> future : List.of(first, second)) {
                try {
                    assertNotNull(future.get(10, TimeUnit.SECONDS));
                    successCount++;
                } catch (ExecutionException ex) {
                    if (ex.getCause() instanceof IdempotencyConflictException) {
                        conflictCount++;
                    } else {
                        throw ex;
                    }
                }
            }

            assertEquals(1, successCount);
            assertEquals(1, conflictCount);

            BigDecimal balance = balanceOf(accountId);
            assertTrue(balance.compareTo(new BigDecimal("100")) == 0 || balance.compareTo(new BigDecimal("200")) == 0);

            assertEquals(1, transactionRepository.count());
            assertEquals(2, ledgerRepository.count());

        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void shouldPreventConcurrentWithdrawalsFromOverspending() throws Exception {

        Long accountId = createAccount("user-1", "IRR").getId();

        walletService.deposit(accountId, "user-1", new BigDecimal("100"), "initial-deposit");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<Long> first = pool.submit(() -> {
                ready.countDown();
                start.await();

                return walletService.withdraw(accountId, "user-1", new BigDecimal("80"), "withdraw-key-1");
            });

            Future<Long> second = pool.submit(() -> {
                ready.countDown();
                start.await();

                return walletService.withdraw(accountId, "user-1", new BigDecimal("80"), "withdraw-key-2");
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successCount = 0;
            int failureCount = 0;

            for (Future<Long> future : List.of(first, second)) {
                try {
                    assertNotNull(future.get(10, TimeUnit.SECONDS));
                    successCount++;
                } catch (ExecutionException ex) {
                    if (ex.getCause() instanceof RuntimeException) {
                        failureCount++;
                    } else {
                        throw ex;
                    }
                }
            }

            assertEquals(1, successCount);
            assertEquals(1, failureCount);

            assertEquals(0, new BigDecimal("20").compareTo(balanceOf(accountId)));

            assertEquals(2, transactionRepository.count());
            assertEquals(4, ledgerRepository.count());

            assertCustomerBalanceMatchesLedger(accountId);
            assertEveryTransactionHasTwoBalancedEntries();

        } finally {
            pool.shutdownNow();
        }
    }


    @Test
    void shouldRejectWithdrawalFromAnotherUsersAccount() {
        Account account = createAccount("user-1", "IRR");

        walletService.deposit(
                account.getId(),
                "user-1",
                new BigDecimal("500"),
                "ownership-withdraw-deposit");

        assertThrows(
                ResourceNotFoundException.class,
                () -> walletService.withdraw(
                        account.getId(),
                        "user-2",
                        new BigDecimal("100"),
                        "ownership-withdraw-attempt")
        );

        assertBalance(account.getId(), "500");
        assertEquals(1, transactionRepository.count());
        assertEquals(2, ledgerRepository.count());
    }

    @Test
    void shouldRejectTransferFromAnotherUsersAccount() {
        Account source = createAccount("user-1", "IRR");
        Account target = createAccount("user-2", "IRR");

        walletService.deposit(
                source.getId(),
                "user-1",
                new BigDecimal("500"),
                "ownership-transfer-deposit");

        assertThrows(
                ResourceNotFoundException.class,
                () -> walletService.transfer(
                        source.getId(),
                        target.getId(),
                        "user-2",
                        new BigDecimal("100"),
                        "ownership-transfer-attempt")
        );

        assertBalance(source.getId(), "500");
        assertBalance(target.getId(), "0");
        assertEquals(1, transactionRepository.count());
        assertEquals(2, ledgerRepository.count());
    }

    @Test
    void shouldNotExposeAnotherUsersTransactionThroughIdempotencyKey() {
        Account account = createAccount("user-1", "IRR");

        walletService.deposit(
                account.getId(),
                "user-1",
                new BigDecimal("500"),
                "ownership-idempotency-key");

        assertThrows(
                ResourceNotFoundException.class,
                () -> walletService.deposit(
                        account.getId(),
                        "user-2",
                        new BigDecimal("500"),
                        "ownership-idempotency-key")
        );

        assertBalance(account.getId(), "500");
        assertEquals(1, transactionRepository.count());
        assertEquals(2, ledgerRepository.count());
    }


    @Test
    void shouldKeepDebitAndCreditBalancedForTransfer() {
        Account source = accountRepository.saveAndFlush(
                new Account("user-1", "IRR"));

        Account target = accountRepository.saveAndFlush(
                new Account("user-2", "IRR"));

        walletService.deposit(
                source.getId(),
                "user-1",
                new BigDecimal("100.00"),
                "balance-check-deposit-001"
        );

        Long transactionId = walletService.transfer(
                source.getId(),
                target.getId(),
                "user-1",
                new BigDecimal("100.00"),
                "balance-check-transfer-001"
        );

        List<LedgerEntry> entries =
                ledgerRepository
                        .findByTransaction_IdOrderByCreatedAtAsc(
                                transactionId);

        BigDecimal totalDebit = entries.stream()
                .filter(e -> e.getType() == LedgerEntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = entries.stream()
                .filter(e -> e.getType() == LedgerEntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(2, entries.size());
        assertEquals(0, totalDebit.compareTo(totalCredit));
        assertEquals(0, totalDebit.compareTo(new BigDecimal("100.00")));
    }


    @Test
    void shouldKeepClearingBalanceConsistentWithLedger() {
        Account source = createAccount("user-1", "IRR");
        Account target = createAccount("user-2", "IRR");

        walletService.deposit(
                source.getId(), "user-1",
                new BigDecimal("1000"), "clearing-test-deposit");

        walletService.transfer(
                source.getId(), target.getId(), "user-1",
                new BigDecimal("300"), "clearing-test-transfer");

        walletService.withdraw(
                target.getId(), "user-2",
                new BigDecimal("100"), "clearing-test-withdraw");

        assertClearingBalanceMatchesLedger();
        assertEveryTransactionHasTwoBalancedEntries();
    }

    @Test
    void shouldRollbackDepositWhenLedgerPersistenceFails() {
        Account account = createAccount("user-1", "IRR");

        assertThrows(
                RuntimeException.class,
                () -> {
                    doThrow(new DataIntegrityViolationException(
                            "Simulated ledger persistence failure"))
                            .when(ledgerRepositorySpy)
                            .saveAll(anyList());

                    walletService.deposit(
                            account.getId(),
                            "user-1",
                            new BigDecimal("500"),
                            "deposit-rollback-test");
                }
        );

        assertBalance(account.getId(), "0");

        assertEquals(
                0,
                transactionRepository.count(),
                "Transaction must be rolled back"
        );

        assertEquals(
                0,
                ledgerRepository.count(),
                "Ledger entries must be rolled back"
        );

        assertClearingBalanceMatchesLedger();
    }


    @Test
    void shouldHandleConcurrentTransfersInOppositeDirections()
            throws Exception {

        Account first = createAccount("user-1", "IRR");
        Account second = createAccount("user-2", "IRR");

        walletService.deposit(
                first.getId(), "user-1",
                new BigDecimal("1000"), "reverse-transfer-deposit-1");

        walletService.deposit(
                second.getId(), "user-2",
                new BigDecimal("1000"), "reverse-transfer-deposit-2");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<Long> firstToSecond = pool.submit(() -> {
                ready.countDown();
                start.await();

                return walletService.transfer(
                        first.getId(), second.getId(), "user-1",
                        new BigDecimal("100"), "transfer-first-to-second");
            });

            Future<Long> secondToFirst = pool.submit(() -> {
                ready.countDown();
                start.await();

                return walletService.transfer(
                        second.getId(), first.getId(), "user-2",
                        new BigDecimal("200"), "transfer-second-to-first");
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            assertNotNull(firstToSecond.get(10, TimeUnit.SECONDS));
            assertNotNull(secondToFirst.get(10, TimeUnit.SECONDS));

        } finally {
            pool.shutdownNow();
        }

        assertBalance(first.getId(), "1100");
        assertBalance(second.getId(), "900");

        assertCustomerBalanceMatchesLedger(first.getId());
        assertCustomerBalanceMatchesLedger(second.getId());
        assertEveryTransactionHasTwoBalancedEntries();

        assertEquals(4, transactionRepository.count());
        assertEquals(8, ledgerRepository.count());
    }

    @Test
    void shouldKeepGlobalLedgerBalanced() {
        List<LedgerEntry> entries = ledgerRepository.findAll();

        BigDecimal totalDebits = entries.stream()
                .filter(e -> e.getType() == LedgerEntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = entries.stream()
                .filter(e -> e.getType() == LedgerEntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(
                0,
                totalDebits.compareTo(totalCredits),
                "Global ledger debits must equal credits"
        );
    }
}
