package com.example.fraud.rule;

import com.example.fraud.dto.ScoringRequest;

/** Counts an account's transactions in the velocity window, including the current one. */
public interface VelocityCounter {

    int countInWindow(ScoringRequest request, int windowMinutes);
}
