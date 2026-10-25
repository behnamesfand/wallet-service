package com.project.wallet_service.currency;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CurrencyPolicy {

    private static final Set<String> SUPPORTED_CURRENCIES =
            Set.of("IRR");

    public void validateSupportedCurrency(String currency) {
        if (currency == null
                || !SUPPORTED_CURRENCIES.contains(currency)) {
            throw new IllegalArgumentException(
                    "Unsupported currency: " + currency
            );
        }
    }

    public boolean isSupported(String currency) {
        return currency != null
                && SUPPORTED_CURRENCIES.contains(currency);
    }
}