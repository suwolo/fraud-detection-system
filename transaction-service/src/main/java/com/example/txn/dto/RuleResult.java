package com.example.txn.dto;

public record RuleResult(String rule, boolean fired, int weight, String explanation) {
}