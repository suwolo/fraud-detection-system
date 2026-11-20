package com.example.txn.dto;

import java.math.BigDecimal;
import java.util.Map;

public record StatisticsResponse(
        long count,
        BigDecimal sum,
        BigDecimal average,
        BigDecimal min,
        BigDecimal max,
        Map<String, CategoryStats> byCategory) {
}