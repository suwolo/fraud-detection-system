package com.example.fraud.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Validates: Requirements 3.7, 4.5 (weights outside 0..100 or non-numeric fail startup naming the rule). */
class FraudPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @EnableConfigurationProperties(FraudProperties.class)
    static class Config {
    }

    @ParameterizedTest
    @CsvSource({
            "high-amount,-1",
            "high-amount,101",
            "high-amount,abc",
            "velocity,-1",
            "velocity,101",
            "velocity,abc",
            "country-mismatch,-1",
            "country-mismatch,101",
            "country-mismatch,abc",
            "unusual-hour,-1",
            "unusual-hour,101",
            "unusual-hour,abc"
    })
    void invalidWeightFailsStartupWithRuleName(String rule, String weight) {
        runner.withPropertyValues("fraud.rules." + rule + ".weight=" + weight)
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    Throwable failure = ctx.getStartupFailure();
                    StringBuilder messages = new StringBuilder();
                    for (Throwable t = failure; t != null; t = t.getCause()) {
                        messages.append(t.getMessage()).append('\n');
                    }
                    assertThat(messages.toString()).contains(rule);
                });
    }

    @Test
    void validWeightsAtBoundsAreAccepted() {
        runner.withPropertyValues(
                        "fraud.rules.high-amount.weight=0",
                        "fraud.rules.velocity.weight=100")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    FraudProperties props = ctx.getBean(FraudProperties.class);
                    assertThat(props.getRules().getHighAmount().getWeight()).isZero();
                    assertThat(props.getRules().getVelocity().getWeight()).isEqualTo(100);
                });
    }

    @Test
    void defaultsMatchDesign() {
        runner.run(ctx -> {
            FraudProperties props = ctx.getBean(FraudProperties.class);
            assertThat(props.getFlagThreshold()).isEqualTo(60);
            assertThat(props.getRules().getHighAmount().getWeight()).isEqualTo(40);
            assertThat(props.getRules().getVelocity().getWeight()).isEqualTo(30);
            assertThat(props.getRules().getCountryMismatch().getWeight()).isEqualTo(50);
            assertThat(props.getRules().getUnusualHour().getWeight()).isEqualTo(25);
        });
    }
}
