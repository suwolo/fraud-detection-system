package com.example.fraud.dto;

import java.util.List;

public record Decision(
        int riskScore,
        boolean flagged,
        List<RuleResult> ruleResults) {
}