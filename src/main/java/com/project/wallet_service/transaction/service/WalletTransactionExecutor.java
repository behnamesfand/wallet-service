
package com.project.wallet_service.transaction.service;

import com.project.wallet_service.account.entity.Account;
import com.project.wallet_service.account.repository.AccountRepository;
import com.project.wallet_service.account.service.SystemAccountService;
import com.project.wallet_service.common.ResourceNotFoundException;
import com.project.wallet_service.ledger.entity.LedgerEntry;
import com.project.wallet_service.ledger.enums.LedgerEntryType;
import com.project.wallet_service.ledger.repository.LedgerEntryRepository;
import com.project.wallet_service.transaction.entity.WalletTransaction;
import com.project.wallet_service.transaction.enums.TransactionType;
import com.project.wallet_service.transaction.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WalletTransactionExecutor {

    private final AccountRepository accountRepository;
    private final WalletTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final SystemAccountService systemAccountService;

    public WalletTransactionExecutor(
            AccountRepository accountRepository,
            WalletTransactionRepository transactionRepository,
            LedgerEntryRepository ledgerRepository, SystemAccountService systemAccountService) {

        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerRepository = ledgerRepository;
        this.systemAccountService = systemAccountService;
    }


    @Transactional
    public Long deposit(
            Long accountId,
            BigDecimal amount,
            String idempotencyKey) {

        Account account = findAccountForUpdate(accountId);
        Account clearingAccount =
                systemAccountService.getClearingAccountForUpdate(
                        account.getCurrency());

        WalletTransaction tx = new WalletTransaction(
                idempotencyKey,
                TransactionType.DEPOSIT,
                amount,
                account.getCurrency(),
                clearingAccount,
                account
        );

        transactionRepository.saveAndFlush(tx);

        clearingAccount.debit(amount);
        account.credit(amount);

        ledgerRepository.saveAll(List.of(
                new LedgerEntry(
                        clearingAccount, tx, LedgerEntryType.DEBIT, amount),
                new LedgerEntry(
                        account, tx, LedgerEntryType.CREDIT, amount)
        ));

        tx.complete();

        return tx.getId();
    }


    @Transactional
    public Long withdraw(
            Long accountId,
            BigDecimal amount,
            String idempotencyKey) {

        Account account = findAccountForUpdate(accountId);

        Account clearingAccount =
                systemAccountService.getClearingAccountForUpdate(
                        account.getCurrency());

        WalletTransaction tx = new WalletTransaction(
                idempotencyKey,
                TransactionType.WITHDRAWAL,
                amount,
                account.getCurrency(),
                account,
                clearingAccount
        );

        transactionRepository.saveAndFlush(tx);

        account.debit(amount);
        clearingAccount.credit(amount);

        ledgerRepository.saveAll(List.of(
                new LedgerEntry(
                        account, tx, LedgerEntryType.DEBIT, amount),
                new LedgerEntry(
                        clearingAccount, tx, LedgerEntryType.CREDIT, amount)
        ));

        tx.complete();

        return tx.getId();
    }


    @Transactional
    public Long transfer(
            Long sourceAccountId,
            Long targetAccountId,
            BigDecimal amount,
            String idempotencyKey) {

        if (sourceAccountId.equals(targetAccountId)) {
            throw new IllegalArgumentException(
                    "Source and target accounts must differ");
        }

        Long firstId = Math.min(sourceAccountId, targetAccountId);
        Long secondId = Math.max(sourceAccountId, targetAccountId);

        Account first = findAccountForUpdate(firstId);
        Account second = findAccountForUpdate(secondId);

        Account source = sourceAccountId.equals(firstId) ? first : second;
        Account target = targetAccountId.equals(firstId) ? first : second;

        if (!source.getCurrency().equals(target.getCurrency())) {
            throw new IllegalArgumentException(
                    "Account currencies must match");
        }

        WalletTransaction tx = new WalletTransaction(
                idempotencyKey,
                TransactionType.TRANSFER,
                amount,
                source.getCurrency(),
                source,
                target
        );

        transactionRepository.saveAndFlush(tx);

        source.debit(amount);
        target.credit(amount);

        ledgerRepository.saveAll(List.of(
                new LedgerEntry(source, tx, LedgerEntryType.DEBIT, amount),
                new LedgerEntry(target, tx, LedgerEntryType.CREDIT, amount)
        ));

        tx.complete();

        return tx.getId();
    }


    private Account findAccountForUpdate(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + accountId
                ));
    }
}
