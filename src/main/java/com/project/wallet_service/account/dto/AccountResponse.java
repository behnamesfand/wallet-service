package com.project.wallet_service.account.dto;

import com.project.wallet_service.account.entity.Account;

import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        String currency,
        BigDecimal balance
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getCurrency(),
                account.getBalance()
        );
    }
}