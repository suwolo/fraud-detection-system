package com.example.txn.dto;

/** Received from the scored topic: the transaction as scored and the fraud decision. */
public record TransactionScoredEvent(TxDto transaction, Decision decision) {

    public TransactionStatus status() {
        return decision.flagged() ? TransactionStatus.FLAGGED : TransactionStatus.APPROVED;
    }

    public TransactionResponse toResponse() {
        TxDto t = transaction;
        return new TransactionResponse(t.id(), t.accountId(), t.amount(), t.currency(), t.merchantId(),
                t.merchantCategory(), t.country(), t.eventTime(), status(), decision.riskScore(),
                decision.ruleResults());
    }
}
