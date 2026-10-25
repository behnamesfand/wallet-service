
package com.project.wallet_service.ledger.service;

import com.project.wallet_service.account.entity.Account;
import com.project.wallet_service.account.repository.AccountRepository;
import com.project.wallet_service.common.ResourceNotFoundException;
import com.project.wallet_service.ledger.dto.LedgerEntryResponse;
import com.project.wallet_service.ledger.dto.WalletTransactionResponse;
import com.project.wallet_service.ledger.entity.LedgerEntry;
import com.project.wallet_service.ledger.repository.LedgerEntryRepository;
import com.project.wallet_service.transaction.entity.WalletTransaction;
import com.project.wallet_service.transaction.repository.WalletTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final WalletTransactionRepository transactionRepository;

    public LedgerService(
            LedgerEntryRepository ledgerEntryRepository,
            AccountRepository accountRepository,
            WalletTransactionRepository transactionRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getAccountHistory(
            Long accountId,
            String ownerId,
            Pageable pageable) {

        validateId(accountId, "Account ID");
        validateOwnerId(ownerId);

        accountRepository.findByIdAndOwnerId(accountId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + accountId));

        return ledgerEntryRepository
                .findByAccount_Id(accountId, pageable)
                .map(LedgerEntryResponse::from);
    }

    @Transactional(readOnly = true)
    public WalletTransactionResponse getTransactionDetails(
            Long transactionId,
            String ownerId) {

        validateId(transactionId, "Transaction ID");
        validateOwnerId(ownerId);

        WalletTransaction transaction = transactionRepository
                .findAccessibleTransaction(transactionId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found: " + transactionId));

        return toTransactionResponse(transaction);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getTransactionLedger(
            Long transactionId,
            String ownerId) {

        validateId(transactionId, "Transaction ID");
        validateOwnerId(ownerId);

        WalletTransaction transaction = transactionRepository
                .findAccessibleTransaction(transactionId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found: " + transactionId));

        return ledgerEntryRepository
                .findByTransaction_IdOrderByCreatedAtAsc(transactionId)
                .stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }

    private WalletTransactionResponse toTransactionResponse(
            WalletTransaction transaction) {

        return new WalletTransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getSourceAccount() == null
                        ? null
                        : transaction.getSourceAccount().getId(),
                transaction.getTargetAccount() == null
                        ? null
                        : transaction.getTargetAccount().getId(),
                transaction.getCreatedAt());
    }

    private void validateOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Owner ID must not be blank");
        }
    }

    private void validateId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive");
        }
    }

}
