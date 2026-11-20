package com.example.fraud.rule;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.dto.RuleResult;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UnusualHourRuleTest {
    private final UnusualHourRule rule = new UnusualHourRule(new FraudProperties());

    private RuleResult eval(String isoTime, String amount) {
        return rule.evaluate(TestData.request(
                TestData.tx(new BigDecimal(amount), "ID", Instant.parse(isoTime))));
    }

    @Test
    void firesAtThreeAmWithLargeAmount() {
        RuleResult r = eval("2024-01-15T03:00:00Z", "6000000");
        assertThat(r.fired()).isTrue();
        assertThat(r.weight()).isEqualTo(25);
        assertThat(r.rule()).isEqualTo("UnusualHourRule");
        assertThat(r.explanation()).isNotBlank();
    }

    @Test
    void firesAt0459() {
        assertThat(eval("2024-01-15T04:59:00Z", "6000000").fired()).isTrue();
    }

    @Test
    void doesNotFireAt0500() {
        assertThat(eval("2024-01-15T05:00:00Z", "6000000").fired()).isFalse();
    }

    @Test
    void firesWhenAmountEqualsMinimum() {
        assertThat(eval("2024-01-15T02:00:00Z", "5000000").fired()).isTrue();
    }

    @Test
    void doesNotFireWhenAmountBelowMinimum() {
        assertThat(eval("2024-01-15T02:00:00Z", "4999999.99").fired()).isFalse();
    }

    @Test
    void usesUtcHourRegardlessOfOffsetInInput() {
        // 2024-01-15T08:00+07:00 is 01:00 UTC
        assertThat(eval("2024-01-15T08:00:00+07:00", "6000000").fired()).isTrue();
    }
}
