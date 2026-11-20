package com.example.txn.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Internal transaction row as read from the database. */
public record Tx(
        String id,
        String accountId,
        BigDecimal amount,
        String currency,
        String merchantId,
        String merchantCategory,
        String country,
        Instant eventTime) {
}