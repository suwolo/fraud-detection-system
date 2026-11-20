package com.example.fraud.rule;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.dto.RuleResult;
import com.example.fraud.dto.ScoringRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConditionalOnProperty(name = "fraud.rules.high-amount.enabled", matchIfMissing = true)
public class HighAmountRule implements FraudRule {

    private final FraudProperties.HighAmount settings;

    public HighAmountRule(FraudProperties props) {
        this.settings = props.getRules().getHighAmount();
    }

    @Override
    public RuleResult evaluate(ScoringRequest request) {
        BigDecimal amount = request.transaction().amount();
        BigDecimal threshold = settings.getThreshold();
        boolean fired = amount.compareTo(threshold) >= 0;
        String explanation = String.format("Amount %s %s threshold %s",
                amount.toPlainString(), fired ? "reaches" : "is below", threshold.toPlainString());
        return new RuleResult("HighAmountRule", fired, settings.getWeight(), RuleSupport.truncate(explanation));
    }
}
