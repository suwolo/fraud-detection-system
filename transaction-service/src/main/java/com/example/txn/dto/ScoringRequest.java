package com.example.txn.dto;

import java.util.List;

public record ScoringRequest(
        TxDto transaction,
        List<TxDto> recentTransactions,
        PrevTx previousTransaction) {
}