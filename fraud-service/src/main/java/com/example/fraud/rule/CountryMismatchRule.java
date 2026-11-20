package com.example.fraud.rule;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.dto.PrevTx;
import com.example.fraud.dto.RuleResult;
import com.example.fraud.dto.ScoringRequest;
import com.example.fraud.dto.TxDto;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "fraud.rules.country-mismatch.enabled", matchIfMissing = true)
public class CountryMismatchRule implements FraudRule {

    private final FraudProperties.CountryMismatch settings;

    public CountryMismatchRule(FraudProperties props) {
        this.settings = props.getRules().getCountryMismatch();
    }

    @Override
    public RuleResult evaluate(ScoringRequest request) {
        PrevTx prev = request.previousTransaction();
        String explanation;
        boolean fired = false;
        if (prev == null) {
            explanation = "No previous transaction";
        } else {
            TxDto tx = request.transaction();
            long minutes = Duration.between(prev.eventTime(), tx.eventTime()).toMinutes();
            boolean differs = !prev.country().equals(tx.country());
            fired = differs && minutes < settings.getMinTravelMinutes();
            explanation = String.format("Previous country %s, current country %s, %d minutes apart (minimum travel %d)",
                    prev.country(), tx.country(), minutes, settings.getMinTravelMinutes());
        }
        return new RuleResult("CountryMismatchRule", fired, settings.getWeight(), RuleSupport.truncate(explanation));
    }
}
