package com.example.fraud.rule;

import com.example.fraud.dto.RuleResult;
import com.example.fraud.dto.ScoringRequest;

public interface FraudRule {

    RuleResult evaluate(ScoringRequest request);
}