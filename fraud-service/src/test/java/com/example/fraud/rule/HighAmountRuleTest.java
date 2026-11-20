package com.example.fraud.rule;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.dto.RuleResult;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HighAmountRuleTest {
    private final HighAmountRule rule = new HighAmountRule(new FraudProperties());

    private RuleResult eval(String amount) {
        return rule.evaluate(TestData.request(TestData.tx(new BigDecimal(amount), "ID", TestData.NOON)));
    }

    @Test
    void firesWhenAmountAboveThreshold() {
        RuleResult r = eval("20000000");
        assertThat(r.fired()).isTrue();
        assertThat(r.weight()).isEqualTo(40);
        assertThat(r.rule()).isEqualTo("HighAmountRule");
        assertThat(r.explanation()).isNotBlank();
    }

    @Test
    void firesWhenAmountEqualsThreshold() {
        assertThat(eval("10000000").fired()).isTrue();
    }

    @Test
    void doesNotFireJustBelowThreshold() {
        RuleResult r = eval("9999999.99");
        assertThat(r.fired()).isFalse();
        assertThat(r.explanation()).isNotBlank();
    }

    @Test
    void doesNotFireForSmallAmount() {
        assertThat(eval("100").fired()).isFalse();
    }
}
