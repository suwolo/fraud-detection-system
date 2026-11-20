package com.example.fraud.service;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.dto.Decision;
import com.example.fraud.dto.RuleResult;
import com.example.fraud.dto.ScoringRequest;
import com.example.fraud.rule.FraudRule;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 4: Rule order independence.
 *
 * <p>For any list of rules and any permutation of it, the Risk_Score, the flagged value and the
 * set of RuleResult entries are equal.
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 4.6</b>
 */
class ScoringServiceRuleOrderPropertyTest {

    private static final ScoringRequest REQUEST = new ScoringRequest(null, null, null);

    /** Stub rule with a fixed outcome. */
    private record StubRule(String name, boolean fired, int weight) implements FraudRule {
        @Override
        public RuleResult evaluate(ScoringRequest request) {
            return new RuleResult(name, fired, weight, name + (fired ? " fired" : " not fired"));
        }
    }

    /** Stub rule that always throws, to cover the isolation path under reordering. */
    private record ThrowingRule(String name) implements FraudRule {
        @Override
        public RuleResult evaluate(ScoringRequest request) {
            throw new IllegalStateException("boom");
        }
    }

    /** A rule list, a permutation of it, and a flag threshold. */
    private record Case(List<FraudRule> rules, List<FraudRule> permuted, int threshold) {
    }

    private static Case of(int threshold, List<FraudRule> rules, Random rnd) {
        List<FraudRule> permuted = new ArrayList<>(rules);
        Collections.shuffle(permuted, rnd);
        return new Case(rules, permuted, threshold);
    }

    static Stream<Case> cases() {
        Random rnd = new Random(42L);
        List<Case> all = new ArrayList<>();

        // Fixed table
        all.add(of(60, List.of(), rnd));
        all.add(of(60, List.of(new StubRule("A", true, 40)), rnd));
        all.add(of(60, List.of(new StubRule("A", true, 40), new StubRule("B", true, 30)), rnd));
        all.add(of(70, List.of(new StubRule("A", true, 40), new StubRule("B", true, 30)), rnd));
        all.add(of(60, List.of(new StubRule("A", true, 80), new StubRule("B", true, 70),
                new StubRule("C", true, 50)), rnd)); // capped at 100
        all.add(of(60, List.of(new StubRule("A", false, 90), new StubRule("B", true, 10),
                new ThrowingRule("C"), new StubRule("D", true, 0)), rnd));
        all.add(new Case(
                List.of(new StubRule("A", true, 25), new StubRule("B", true, 35), new StubRule("C", false, 50)),
                List.of(new StubRule("C", false, 50), new StubRule("B", true, 35), new StubRule("A", true, 25)),
                60)); // explicit reverse order, boundary score == threshold

        // Seeded random loop: 200 cases
        Random gen = new Random(20240601L);
        for (int i = 0; i < 200; i++) {
            int n = gen.nextInt(9);
            List<FraudRule> rules = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                String name = "Rule" + j;
                if (gen.nextInt(10) == 0) {
                    rules.add(new ThrowingRule(name));
                } else {
                    rules.add(new StubRule(name, gen.nextBoolean(), gen.nextInt(101)));
                }
            }
            all.add(of(gen.nextInt(101), rules, gen));
        }
        return all.stream();
    }

    @ParameterizedTest
    @MethodSource("cases")
    void permutingRulesDoesNotChangeDecision(Case c) {
        Decision original = service(c.rules(), c.threshold()).score(REQUEST);
        Decision reordered = service(c.permuted(), c.threshold()).score(REQUEST);

        assertThat(reordered.riskScore()).isEqualTo(original.riskScore());
        assertThat(reordered.flagged()).isEqualTo(original.flagged());
        assertThat(new HashSet<>(reordered.ruleResults()))
                .isEqualTo(new HashSet<>(original.ruleResults()));
        assertThat(reordered.ruleResults()).hasSameSizeAs(original.ruleResults());
    }

    private static ScoringService service(List<FraudRule> rules, int threshold) {
        FraudProperties props = new FraudProperties();
        props.setFlagThreshold(threshold);
        return new ScoringService(rules, props);
    }
}
