package com.example.fraud.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ScoringRequest(
        @NotNull @Valid TxDto transaction,
        @Valid List<@NotNull @Valid TxDto> recentTransactions,
        @Valid PrevTx previousTransaction) {

    public ScoringRequest {
        recentTransactions = recentTransactions == null ? List.of() : List.copyOf(recentTransactions.stream()
                .filter(java.util.Objects::nonNull).toList());
    }
}