package com.example.fraud.rule;

import com.example.fraud.dto.ScoringRequest;

/** Counts from the history the caller supplied in the request. Needs no external store. */
public class RequestHistoryVelocityCounter implements VelocityCounter {

    @Override
    public int countInWindow(ScoringRequest request, int windowMinutes) {
        return request.recentTransactions().size() + 1;
    }
}
