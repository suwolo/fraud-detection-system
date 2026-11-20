package com.example.fraud.dto;

/** Published to the scored topic after a transaction has been evaluated. */
public record TransactionScoredEvent(TxDto transaction, Decision decision) {
}
