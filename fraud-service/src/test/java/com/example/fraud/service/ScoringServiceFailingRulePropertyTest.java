package com.example.fraud.service;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.dto.Decision;
import com.example.fraud.dto.RuleResult;
import com.example.fraud.dto.ScoringRequest;
import com.example.fraud.rule.FraudRule;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 5: A failing rule is isolated.
 *
 * <p><b>Validates: Requirements 3.6, 9.5</b>
 */
class ScoringServiceFailingRulePropertyTest {

    /** Stub rule description: weight, whether it fires, whether it throws. */
    record Spec(int weight, boolean fired, boolean throwing) {
    }

    private static Spec s(int weight, boolean fired, boolean throwing) {
        return new Spec(weight, fired, throwing);
    }

    static Stream<Arguments> cases() {
        List<List<Spec>> table = List.of(
                List.of(s(40, true, true)),
                List.of(s(40, true, true), s(30, true, false)),
                List.of(s(30, true, false), s(40, true, true)),
                List.of(s(40, true, true), s(40, true, true), s(40, true, true)),
                List.of(s(60, true, false), s(70, true, true), s(50, true, false)),
                List.of(s(60, true, false), s(50, false, false), s(10, false, true)),
                List.of(s(100, true, true), s(0, true, false)));

        List<Arguments> args = new ArrayList<>();
        for (List<Spec> specs : table) {
            args.add(Arguments.of(specs));
        }

        Random random = new Random(42L);
        for (int i = 0; i < 200; i++) {
            int n = 1 + random.nextInt(8);
            List<Spec> specs = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                specs.add(new Spec(random.nextInt(101), random.nextBoolean(), random.nextBoolean()));
            }
            args.add(Arguments.of(specs));
        }
        return args.stream();
    }

    @ParameterizedTest
    @MethodSource("cases")
    void throwingRulesAreIsolated(List<Spec> specs) {
        AtomicInteger evaluated = new AtomicInteger();
        List<FraudRule> rules = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            Spec spec = specs.get(i);
            String name = "stub-" + i;
            rules.add(req -> {
                evaluated.incrementAndGet();
                if (spec.throwing()) {
                    throw new IllegalStateException("boom");
                }
                return new RuleResult(name, spec.fired(), spec.weight(), "stub");
            });
        }
        ScoringService service = new ScoringService(rules, new FraudProperties());

        Decision decision = service.score(new ScoringRequest(null, null, null));

        // every rule is still evaluated, one result per rule
        assertThat(evaluated.get()).isEqualTo(specs.size());
        assertThat(decision.ruleResults()).hasSize(specs.size());

        int expected = 0;
        for (int i = 0; i < specs.size(); i++) {
            Spec spec = specs.get(i);
            RuleResult result = decision.ruleResults().get(i);
            if (spec.throwing()) {
                assertThat(result.fired()).isFalse();
                assertThat(result.weight()).isZero();
            } else {
                assertThat(result.fired()).isEqualTo(spec.fired());
                assertThat(result.weight()).isEqualTo(spec.weight());
                if (spec.fired()) {
                    expected += spec.weight();
                }
            }
        }
        assertThat(decision.riskScore()).isEqualTo(Math.min(100, expected));
    }
}
