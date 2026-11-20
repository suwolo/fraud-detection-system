package com.example.fraud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record PrevTx(
        @NotNull Instant eventTime,
        @NotBlank String country) {
}