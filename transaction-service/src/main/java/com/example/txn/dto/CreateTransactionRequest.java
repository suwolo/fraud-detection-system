package com.example.txn.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateTransactionRequest(
        @NotNull String accountId,
        @NotNull
        @DecimalMin("0.01")
        @DecimalMax("999999999.99")
        @Digits(integer = 9, fraction = 2)
        BigDecimal amount,
        @NotNull
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a three-letter uppercase code")
        String currency,
        @NotNull String merchantId,
        @NotNull String merchantCategory,
        @NotNull String country,
        @NotNull Instant eventTime) {
}