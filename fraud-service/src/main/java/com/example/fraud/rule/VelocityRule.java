package com.example.fraud.rule;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.dto.RuleResult;
import com.example.fraud.dto.ScoringRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fires when the account has more than {@code maxCount} transactions in the window. The count
 * comes from the configured {@link VelocityCounter} (Redis when {@code app.velocity.store=redis},
 * otherwise the history supplied in the request). If the counter fails, the rule falls back to
 * the request history instead of failing.
 */
@Component
@ConditionalOnProperty(name = "fraud.rules.velocity.enabled", matchIfMissing = true)
public class VelocityRule implements FraudRule {

    private static final Logger log = LoggerFactory.getLogger(VelocityRule.class);

    private final FraudProperties.Velocity settings;
    private final VelocityCounter counter;
    private final VelocityCounter fallback = new RequestHistoryVelocityCounter();

    @Autowired
    public VelocityRule(FraudProperties props, ObjectProvider<VelocityCounter> counter) {
        this(props, counter.getIfAvailable(RequestHistoryVelocityCounter::new));
    }

    public VelocityRule(FraudProperties props) {
        this(props, new RequestHistoryVelocityCounter());
    }

    VelocityRule(FraudProperties props, VelocityCounter counter) {
        this.settings = props.getRules().getVelocity();
        this.counter = counter;
    }

    @Override
    public RuleResult evaluate(ScoringRequest request) {
        int count = count(request);
        int max = settings.getMaxCount();
        boolean fired = count > max;
        String explanation = String.format("%d transactions within %d minutes (including current), %s limit of %d",
                count, settings.getWindowMinutes(), fired ? "exceeds" : "within", max);
        return new RuleResult("VelocityRule", fired, settings.getWeight(), RuleSupport.truncate(explanation));
    }

    private int count(ScoringRequest request) {
        try {
            return counter.countInWindow(request, settings.getWindowMinutes());
        } catch (RuntimeException e) {
            log.warn("Velocity counter failed, using request history: {}", e.toString());
            return fallback.countInWindow(request, settings.getWindowMinutes());
        }
    }
}
