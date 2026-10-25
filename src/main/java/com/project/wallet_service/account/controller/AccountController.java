
package com.project.wallet_service.account.controller;

import com.project.wallet_service.account.dto.AccountResponse;
import com.project.wallet_service.account.dto.CreateAccountRequest;
import com.project.wallet_service.account.dto.CreateAccountResponse;
import com.project.wallet_service.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateAccountResponse createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            Authentication authentication) {

        String ownerId = authentication.getName();

        Long accountId = accountService.createAccount(
                ownerId,
                request.currency()
        );

        return new CreateAccountResponse(accountId);
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(
            @PathVariable Long accountId,
            Authentication authentication) {

        String ownerId = authentication.getName();

        return AccountResponse.from(
                accountService.getAccount(accountId, ownerId)
        );
    }
}
