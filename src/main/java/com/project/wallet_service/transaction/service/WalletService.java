
package com.project.wallet_service.transaction.service;

import com.project.wallet_service.account.entity.Account;
import com.project.wallet_service.account.repository.AccountRepository;
import com.project.wallet_service.common.IdempotencyConflictException;
import com.project.wallet_service.common.ResourceNotFoundException;
import com.project.wallet_service.transaction.dto.TransactionResponse;
import com.project.wallet_service.transaction.entity.WalletTransaction;
import com.project.wallet_service.transaction.enums.TransactionStatus;
import com.project.wallet_service.transaction.enums.TransactionType;
import com.project.wallet_service.transaction.repository.WalletTransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

@Service
public class WalletService {

    private final WalletTransactionRepository transactionRepository;
    private final WalletTransactionExecutor executor;
    private final AccountRepository accountRepository;

    public WalletService(
            WalletTransactionRepository transactionRepository,
            WalletTransactionExecutor executor, AccountRepository accountRepository) {

        this.transactionRepository = transactionRepository;
        this.executor = executor;
        this.accountRepository = accountRepository;
    }

    public Long deposit(
            Long accountId,
            String ownerId,
            BigDecimal amount,
            String idempotencyKey) {

        validateRequest(amount, idempotencyKey);
        validateAccountId(accountId);
        validateAccountOwnership(accountId, ownerId);

        Optional<WalletTransaction> existing =
                findExisting(idempotencyKey);

        if (existing.isPresent()) {
            return resolveExisting(
                    existing.get(),
                    TransactionType.DEPOSIT,
                    amount,
                    null,
                    accountId
            );
        }

        try {
            return executor.deposit(accountId, amount, idempotencyKey);
        } catch (DataIntegrityViolationException ex) {
            return resolveDuplicate(
                    idempotencyKey,
                    TransactionType.DEPOSIT,
                    amount,
                    null,
                    accountId,
                    ex
            );
        }
    }

    public Long withdraw(
            Long accountId,
            String ownerId,
            BigDecimal amount,
            String idempotencyKey) {

        validateRequest(amount, idempotencyKey);
        validateAccountId(accountId);
        validateAccountOwnership(accountId, ownerId);

        Optional<WalletTransaction> existing =
                findExisting(idempotencyKey);

        if (existing.isPresent()) {
            return resolveExisting(
                    existing.get(),
                    TransactionType.WITHDRAWAL,
                    amount,
                    accountId,
                    null
            );
        }

        try {
            return executor.withdraw(accountId, amount, idempotencyKey);
        } catch (DataIntegrityViolationException ex) {
            return resolveDuplicate(
                    idempotencyKey,
                    TransactionType.WITHDRAWAL,
                    amount,
                    accountId,
                    null,
                    ex
            );
        }
    }

    public Long transfer(
            Long sourceAccountId,
            Long targetAccountId,
            String ownerId,
            BigDecimal amount,
            String idempotencyKey) {

        validateRequest(amount, idempotencyKey);
        validateAccountId(sourceAccountId);
        validateAccountId(targetAccountId);
        validateAccountOwnership(sourceAccountId, ownerId);

        if (sourceAccountId.equals(targetAccountId)) {
            throw new IllegalArgumentException(
                    "Source and target accounts must differ"
            );
        }

        Optional<WalletTransaction> existing =
                findExisting(idempotencyKey);

        if (existing.isPresent()) {
            return resolveExisting(
                    existing.get(),
                    TransactionType.TRANSFER,
                    amount,
                    sourceAccountId,
                    targetAccountId
            );
        }

        try {
            return executor.transfer(
                    sourceAccountId,
                    targetAccountId,
                    amount,
                    idempotencyKey
            );
        } catch (DataIntegrityViolationException ex) {
            return resolveDuplicate(
                    idempotencyKey,
                    TransactionType.TRANSFER,
                    amount,
                    sourceAccountId,
                    targetAccountId,
                    ex
            );
        }
    }

    private Optional<WalletTransaction> findExisting(String key) {
        return transactionRepository
                .findWithAccountsByIdempotencyKey(key);
    }

    private Long resolveDuplicate(
            String key,
            TransactionType type,
            BigDecimal amount,
            Long sourceId,
            Long targetId,
            DataIntegrityViolationException originalException) {

        Optional<WalletTransaction> existing = findExisting(key);

        if (existing.isEmpty()) {
            throw originalException;
        }

        return resolveExisting(
                existing.get(), type, amount, sourceId, targetId
        );
    }


    private Long resolveExisting(
            WalletTransaction tx,
            TransactionType requestedType,
            BigDecimal requestedAmount,
            Long requestedSourceId,
            Long requestedTargetId) {

        Account source = tx.getSourceAccount();
        Account target = tx.getTargetAccount();

        Long existingSourceId =
                source == null ? null : source.getId();

        Long existingTargetId =
                target == null ? null : target.getId();

        boolean sameSource =
                requestedType == TransactionType.DEPOSIT
                        || Objects.equals(
                        existingSourceId,
                        requestedSourceId
                );

        boolean sameTarget =
                requestedType == TransactionType.WITHDRAWAL
                        || Objects.equals(
                        existingTargetId,
                        requestedTargetId
                );

        boolean sameRequest =
                tx.getType() == requestedType
                        && tx.getStatus() == TransactionStatus.COMPLETED
                        && tx.getAmount().compareTo(requestedAmount) == 0
                        && sameSource
                        && sameTarget;

        if (!sameRequest) {
            throw new IdempotencyConflictException();
        }

        return tx.getId();
    }


    private void validateRequest(
            BigDecimal amount,
            String idempotencyKey) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive"
            );
        }

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency key must contain 1-100 characters"
            );
        }

        BigDecimal normalized;

        try {
            normalized = amount.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "Amount cannot have more than 4 decimal places", ex
            );
        }

        BigDecimal maxAmount = new BigDecimal("999999999999999.9999");

        if (normalized.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException(
                    "Amount exceeds supported precision"
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

    private void validateAccountOwnership(
            Long accountId,
            String ownerId) {

        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Owner ID must not be blank"
            );
        }

        accountRepository.findByIdAndOwnerId(accountId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + accountId
                ));
    }


    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(
            Long transactionId,
            String ownerId) {

        if (transactionId == null || transactionId <= 0) {
            throw new IllegalArgumentException(
                    "Transaction ID must be positive");
        }

        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Owner ID must not be blank");
        }

        WalletTransaction transaction = transactionRepository
                .findAccessibleTransaction(transactionId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found: " + transactionId));

        return TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistory(
            String ownerId,
            Pageable pageable) {

        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Owner ID must not be blank");
        }

        return transactionRepository
                .findTransactionsForOwner(ownerId, pageable)
                .map(TransactionResponse::from);
    }
}
