package com.example.fraud.rule;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.dto.RuleResult;
import com.example.fraud.dto.ScoringRequest;
import com.example.fraud.dto.TxDto;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(name = "fraud.rules.unusual-hour.enabled", matchIfMissing = true)
public class UnusualHourRule implements FraudRule {

    private final FraudProperties.UnusualHour settings;

    public UnusualHourRule(FraudProperties props) {
        this.settings = props.getRules().getUnusualHour();
    }

    @Override
    public RuleResult evaluate(ScoringRequest request) {
        TxDto tx = request.transaction();
        int hour = tx.eventTime().atOffset(ZoneOffset.UTC).getHour();
        boolean inWindow = hour >= settings.getStartHour() && hour < settings.getEndHour();
        boolean largeEnough = tx.amount().compareTo(settings.getMinAmount()) >= 0;
        boolean fired = inWindow && largeEnough;
        String explanation = String.format("UTC hour %d %s window [%d, %d); amount %s %s minimum %s",
                hour, inWindow ? "is inside" : "is outside", settings.getStartHour(), settings.getEndHour(),
                tx.amount().toPlainString(), largeEnough ? "reaches" : "is below", settings.getMinAmount().toPlainString());
        return new RuleResult("UnusualHourRule", fired, settings.getWeight(), RuleSupport.truncate(explanation));
    }
}
