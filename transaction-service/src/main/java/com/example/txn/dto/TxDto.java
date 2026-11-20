package com.example.txn.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Transaction as sent to Fraud_Service. */
public record TxDto(
        String id,
        String accountId,
        BigDecimal amount,
        String currency,
        String merchantId,
        String merchantCategory,
        String country,
        Instant eventTime) {
}