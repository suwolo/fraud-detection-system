package com.example.txn.dto;

import java.math.BigDecimal;

public record RiskyMerchant(String merchantId, long flaggedCount, BigDecimal avgRiskScore) {
}