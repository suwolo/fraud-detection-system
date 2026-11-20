package com.example.fraud.config;

import java.util.Map;

/**
 * Startup check of rule weights. Fails with the rule name and the invalid weight.
 */
public final class RuleWeightValidator {

    public static final int MIN_WEIGHT = 0;
    public static final int MAX_WEIGHT = 100;

    private RuleWeightValidator() {
    }

    public static void validate(Map<String, Integer> weightsByRule) {
        weightsByRule.forEach((rule, weight) -> {
            if (weight == null || weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
                throw new IllegalStateException("Invalid weight for rule '" + rule + "': " + weight
                        + " (must be between " + MIN_WEIGHT + " and " + MAX_WEIGHT + ")");
            }
        });
    }
}