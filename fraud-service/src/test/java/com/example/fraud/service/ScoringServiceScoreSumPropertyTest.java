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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1: Score is the capped sum of fired weights.
 *
 * <p><b>Validates: Requirements 3.2, 9.2</b>
 */
class ScoringServiceScoreSumPropertyTest {

    /** One stub rule description: weight 0..100 and whether it fires. */
    record Spec(int weight, boolean fired) {
    }

    private static Spec s(int weight, boolean fired) {
        return new Spec(weight, fired);
    }

    static Stream<Arguments> cases() {
        List<List<Spec>> table = List.of(
                List.of(),
                List.of(s(40, true)),
                List.of(s(40, false)),
                List.of(s(0, true), s(0, true)),
                List.of(s(100, true)),
                List.of(s(100, true), s(100, true)),
                List.of(s(60, true), s(40, true)),
                List.of(s(60, true), s(41, true)),
                List.of(s(60, true), s(50, false), s(30, true)),
                List.of(s(40, true), s(30, true), s(50, true), s(25, true)),
                List.of(s(40, false), s(30, false), s(50, false), s(25, false)));

        List<Arguments> args = new ArrayList<>();
        for (List<Spec> specs : table) {
            args.add(Arguments.of(specs));
        }

        Random random = new Random(42L);
        for (int i = 0; i < 200; i++) {
            int n = random.nextInt(9);
            List<Spec> specs = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                specs.add(new Spec(random.nextInt(101), random.nextBoolean()));
            }
            args.add(Arguments.of(specs));
        }
        return args.stream();
    }

    @ParameterizedTest
    @MethodSource("cases")
    void scoreIsCappedSumOfFiredWeights(List<Spec> specs) {
        List<FraudRule> rules = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            Spec spec = specs.get(i);
            String name = "stub-" + i;
            rules.add(req -> new RuleResult(name, spec.fired(), spec.weight(), "stub"));
        }
        ScoringService service = new ScoringService(rules, new FraudProperties());

        Decision decision = service.score(new ScoringRequest(null, null, null));

        int expected = Math.min(100, specs.stream()
                .filter(Spec::fired)
                .mapToInt(Spec::weight)
                .sum());
        assertThat(decision.riskScore()).isEqualTo(expected);
        assertThat(decision.riskScore()).isBetween(0, 100);
    }
}
