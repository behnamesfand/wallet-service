
package com.project.wallet_service.ledger.controller;

import com.project.wallet_service.ledger.dto.LedgerEntryResponse;
import com.project.wallet_service.ledger.dto.WalletTransactionResponse;
import com.project.wallet_service.ledger.service.LedgerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/accounts/{accountId}")
    public Page<LedgerEntryResponse> getAccountHistory(
            @PathVariable Long accountId,
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        validatePagination(page, size);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")));

        return ledgerService.getAccountHistory(
                accountId,
                authentication.getName(),
                pageable);
    }

    @GetMapping("/transactions/{transactionId}")
    public WalletTransactionResponse getTransactionDetails(
            @PathVariable Long transactionId,
            Authentication authentication) {

        return ledgerService.getTransactionDetails(
                transactionId,
                authentication.getName());
    }

    @GetMapping("/transactions/{transactionId}/entries")
    public List<LedgerEntryResponse> getTransactionLedger(
            @PathVariable Long transactionId,
            Authentication authentication) {

        return ledgerService.getTransactionLedger(
                transactionId,
                authentication.getName());
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page must not be negative");
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100");
        }
    }
}
