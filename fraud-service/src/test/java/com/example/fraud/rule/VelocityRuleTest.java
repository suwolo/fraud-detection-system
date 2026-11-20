package com.example.fraud.rule;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.dto.RuleResult;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VelocityRuleTest {
    private final VelocityRule rule = new VelocityRule(new FraudProperties());

    private RuleResult eval(int recentCount) {
        return rule.evaluate(TestData.request(
                TestData.tx(new java.math.BigDecimal("1000"), "ID", TestData.NOON),
                TestData.recent(recentCount), null));
    }

    @Test
    void firesWithFiveRecentTransactions() {
        RuleResult r = eval(5);
        assertThat(r.fired()).isTrue();
        assertThat(r.weight()).isEqualTo(30);
        assertThat(r.rule()).isEqualTo("VelocityRule");
        assertThat(r.explanation()).isNotBlank();
    }

    @Test
    void doesNotFireWithFourRecentTransactions() {
        assertThat(eval(4).fired()).isFalse();
    }

    @Test
    void doesNotFireWithNoRecentTransactions() {
        assertThat(eval(0).fired()).isFalse();
    }
}
