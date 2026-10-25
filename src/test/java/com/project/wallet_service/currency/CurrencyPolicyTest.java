package com.project.wallet_service.currency;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyPolicyTest {

    private CurrencyPolicy currencyPolicy;

    @BeforeEach
    void setUp() {
        currencyPolicy = new CurrencyPolicy();
    }

    @Test
    void shouldAcceptSupportedCurrency() {
        assertDoesNotThrow(
                () -> currencyPolicy.validateSupportedCurrency("IRR")
        );
    }

    @Test
    void shouldRejectUnsupportedCurrency() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> currencyPolicy.validateSupportedCurrency("USD")
        );

        assertEquals(
                "Unsupported currency: USD",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullCurrency() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> currencyPolicy.validateSupportedCurrency(null)
        );

        assertEquals(
                "Unsupported currency: null",
                exception.getMessage()
        );
    }

    @Test
    void shouldReturnTrueForSupportedCurrency() {
        assertTrue(currencyPolicy.isSupported("IRR"));
    }

    @Test
    void shouldReturnFalseForUnsupportedCurrency() {
        assertFalse(currencyPolicy.isSupported("USD"));
    }

    @Test
    void shouldReturnFalseForNullCurrency() {
        assertFalse(currencyPolicy.isSupported(null));
    }

    @Test
    void shouldRejectLowercaseCurrency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> currencyPolicy.validateSupportedCurrency("irr")
        );
    }

    @Test
    void shouldRejectEmptyCurrency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> currencyPolicy.validateSupportedCurrency("")
        );
    }

    @Test
    void shouldRejectBlankCurrency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> currencyPolicy.validateSupportedCurrency("   ")
        );
    }
}