package com.example.txn.search;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One search result. Only the names of the rules that fired are indexed, not their weights. */
public record TransactionSearchHit(
        String id,
        String accountId,
        BigDecimal amount,
        String currency,
        String merchantId,
        String merchantCategory,
        String country,
        Instant eventTime,
        String status,
        Integer riskScore,
        List<String> firedRules) {
}
