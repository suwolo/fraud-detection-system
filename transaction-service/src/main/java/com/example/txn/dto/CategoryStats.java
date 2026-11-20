package com.example.txn.dto;

import java.math.BigDecimal;

public record CategoryStats(long count, BigDecimal total, BigDecimal average) {
}