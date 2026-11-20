package com.example.fraud.service;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.dto.Decision;
import com.example.fraud.dto.RuleResult;
import com.example.fraud.dto.ScoringRequest;
import com.example.fraud.rule.FraudRule;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScoringService {

    private final List<FraudRule> rules;
    private final int flagThreshold;

    public ScoringService(List<FraudRule> rules, FraudProperties props) {
        this.rules = List.copyOf(rules);
        this.flagThreshold = props.getFlagThreshold();
    }

    public Decision score(ScoringRequest req) {
        List<RuleResult> results = rules.stream()
                .map(rule -> safeEvaluate(rule, req))
                .toList();
        int score = Math.min(100, results.stream()
                .filter(RuleResult::fired)
                .mapToInt(RuleResult::weight)
                .sum());
        return new Decision(score, score >= flagThreshold, results);
    }

    private RuleResult safeEvaluate(FraudRule rule, ScoringRequest req) {
        try {
            return rule.evaluate(req);
        } catch (RuntimeException e) {
            return new RuleResult(rule.getClass().getSimpleName(), false, 0, "evaluation failed");
        }
    }
}
