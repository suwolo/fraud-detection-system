package com.example.fraud.rule;

/** Shared helpers for rule implementations. */
final class RuleSupport {

    static final int MAX_EXPLANATION = 500;

    private RuleSupport() {
    }

    static String truncate(String text) {
        return text.length() <= MAX_EXPLANATION ? text : text.substring(0, MAX_EXPLANATION);
    }
}
