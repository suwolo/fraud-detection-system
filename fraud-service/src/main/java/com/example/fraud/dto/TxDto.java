package com.example.fraud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record TxDto(
        @NotBlank String id,
        @NotBlank String accountId,
        @NotNull BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String merchantId,
        @NotBlank String merchantCategory,
        @NotBlank String country,
        @NotNull Instant eventTime) {
}