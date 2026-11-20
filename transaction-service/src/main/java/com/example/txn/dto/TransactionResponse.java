package com.example.txn.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TransactionResponse(
        String id,
        String accountId,
        BigDecimal amount,
        String currency,
        String merchantId,
        String merchantCategory,
        String country,
        Instant eventTime,
        TransactionStatus status,
        Integer riskScore,
        List<RuleResult> ruleResults) {
}