package com.example.fraud.rule;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.dto.PrevTx;
import com.example.fraud.dto.RuleResult;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CountryMismatchRuleTest {
    private final CountryMismatchRule rule = new CountryMismatchRule(new FraudProperties());

    private RuleResult eval(String prevCountry, long minutesApart, String currentCountry) {
        Instant now = TestData.NOON;
        PrevTx prev = new PrevTx(now.minusSeconds(minutesApart * 60), prevCountry);
        return rule.evaluate(TestData.request(
                TestData.tx(new BigDecimal("1000"), currentCountry, now), java.util.List.of(), prev));
    }

    @Test
    void firesWhenCountryDiffersWithin119Minutes() {
        RuleResult r = eval("ID", 119, "SG");
        assertThat(r.fired()).isTrue();
        assertThat(r.weight()).isEqualTo(50);
        assertThat(r.rule()).isEqualTo("CountryMismatchRule");
        assertThat(r.explanation()).isNotBlank();
    }

    @Test
    void doesNotFireAt120Minutes() {
        assertThat(eval("ID", 120, "SG").fired()).isFalse();
    }

    @Test
    void doesNotFireForSameCountryEvenWhenClose() {
        assertThat(eval("ID", 5, "ID").fired()).isFalse();
    }

    @Test
    void doesNotFireWhenPreviousIsNull() {
        RuleResult r = rule.evaluate(TestData.request(
                TestData.tx(new BigDecimal("1000"), "SG", TestData.NOON)));
        assertThat(r.fired()).isFalse();
        assertThat(r.explanation()).isNotBlank();
    }
}
