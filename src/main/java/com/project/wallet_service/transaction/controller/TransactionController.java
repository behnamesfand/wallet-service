
package com.project.wallet_service.transaction.controller;

import com.project.wallet_service.transaction.dto.*;
import com.project.wallet_service.transaction.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final WalletService walletService;

    public TransactionController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateTransactionResponse deposit(
            @Valid @RequestBody DepositRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {

        Long transactionId = walletService.deposit(
                request.accountId(),
                authentication.getName(),
                request.amount(),
                idempotencyKey
        );

        return new CreateTransactionResponse(transactionId);
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateTransactionResponse withdraw(
            @Valid @RequestBody WithdrawRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {

        Long transactionId = walletService.withdraw(
                request.accountId(),
                authentication.getName(),
                request.amount(),
                idempotencyKey
        );

        return new CreateTransactionResponse(transactionId);
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateTransactionResponse transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {

        Long transactionId = walletService.transfer(
                request.sourceAccountId(),
                request.targetAccountId(),
                authentication.getName(),
                request.amount(),
                idempotencyKey
        );

        return new CreateTransactionResponse(transactionId);
    }

    @GetMapping("/{transactionId}")
    public TransactionResponse getTransaction(
            @PathVariable Long transactionId,
            Authentication authentication) {

        return walletService.getTransaction(
                transactionId,
                authentication.getName()
        );
    }

    @GetMapping
    public Page<TransactionResponse> getTransactionHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page must not be negative");
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Size must be between 1 and 100");
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        return walletService.getTransactionHistory(
                authentication.getName(),
                pageable
        );
    }
}
