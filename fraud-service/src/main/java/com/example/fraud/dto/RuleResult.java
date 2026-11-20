package com.example.fraud.dto;

import jakarta.validation.constraints.NotBlank;

public record RuleResult(
        @NotBlank String rule,
        boolean fired,
        int weight,
        @NotBlank String explanation) {
}