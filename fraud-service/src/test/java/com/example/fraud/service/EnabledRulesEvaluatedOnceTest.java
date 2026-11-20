package com.example.fraud.service;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.dto.Decision;
import com.example.fraud.dto.RuleResult;
import com.example.fraud.dto.ScoringRequest;
import com.example.fraud.rule.FraudRule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 3: Every enabled rule is evaluated once, disabled rules are absent.
 *
 * <p><b>Validates: Requirements 3.1, 4.3, 9.4</b>
 *
 * <p>A disabled rule is not registered as a bean, so "disabled" is modelled by leaving the
 * stub out of the list given to {@link ScoringService}.
 */
class EnabledRulesEvaluatedOnceTest {

    private static final int RANDOM_CASES = 200;
    private static final long SEED = 20240603L;
    private static final ScoringRequest REQUEST = new ScoringRequest(null, null, null);

    /** Stub rule that counts invocations. */
    private static final class CountingRule implements FraudRule {
        private final String name;
        private final boolean fired;
        private final int weight;
        private final AtomicInteger calls = new AtomicInteger();

        CountingRule(String name, boolean fired, int weight) {
            this.name = name;
            this.fired = fired;
            this.weight = weight;
        }

        @Override
        public RuleResult evaluate(ScoringRequest request) {
            calls.incrementAndGet();
            return new RuleResult(name, fired, weight, "stub " + name);
        }
    }

    /** One case: the full universe of rules and which of them are enabled (injected). */
    private record Case(List<CountingRule> universe, List<Boolean> enabled) {
        List<CountingRule> injected() {
            List<CountingRule> list = new ArrayList<>();
            for (int i = 0; i < universe.size(); i++) {
                if (enabled.get(i)) {
                    list.add(universe.get(i));
                }
            }
            return list;
        }
    }

    private static Case fixed(boolean[] enabled, int[] weights, boolean[] fired) {
        List<CountingRule> universe = new ArrayList<>();
        List<Boolean> en = new ArrayList<>();
        for (int i = 0; i < enabled.length; i++) {
            universe.add(new CountingRule("rule-" + i, fired[i], weights[i]));
            en.add(enabled[i]);
        }
        return new Case(universe, en);
    }

    static Stream<Case> cases() {
        List<Case> all = new ArrayList<>();
        // fixed table
        all.add(fixed(new boolean[]{}, new int[]{}, new boolean[]{}));
        all.add(fixed(new boolean[]{true}, new int[]{40}, new boolean[]{true}));
        all.add(fixed(new boolean[]{false}, new int[]{40}, new boolean[]{true}));
        all.add(fixed(new boolean[]{true, true, true, true}, new int[]{40, 30, 50, 25},
                new boolean[]{true, true, true, true}));
        all.add(fixed(new boolean[]{true, false, true, false}, new int[]{40, 30, 50, 25},
                new boolean[]{true, true, true, true}));
        all.add(fixed(new boolean[]{false, false, false, false}, new int[]{40, 30, 50, 25},
                new boolean[]{true, true, true, true}));
        all.add(fixed(new boolean[]{true, true, false, true}, new int[]{0, 100, 100, 7},
                new boolean[]{false, true, true, false}));
        // seeded random loop
        Random random = new Random(SEED);
        for (int i = 0; i < RANDOM_CASES; i++) {
            int n = random.nextInt(9);
            boolean[] enabled = new boolean[n];
            boolean[] fired = new boolean[n];
            int[] weights = new int[n];
            for (int j = 0; j < n; j++) {
                enabled[j] = random.nextBoolean();
                fired[j] = random.nextBoolean();
                weights[j] = random.nextInt(101);
            }
            all.add(fixed(enabled, weights, fired));
        }
        return all.stream();
    }

    @ParameterizedTest
    @MethodSource("cases")
    void everyInjectedRuleIsEvaluatedExactlyOnceAndLeftOutRulesAreAbsent(Case c) {
        List<CountingRule> injected = c.injected();
        ScoringService service = new ScoringService(new ArrayList<FraudRule>(injected), new FraudProperties());

        Decision decision = service.score(REQUEST);

        // one result per injected rule, in the injected order
        assertThat(decision.ruleResults().stream().map(RuleResult::rule).collect(Collectors.toList()))
                .isEqualTo(injected.stream().map(r -> r.name).collect(Collectors.toList()));

        for (int i = 0; i < c.universe().size(); i++) {
            CountingRule rule = c.universe().get(i);
            if (c.enabled().get(i)) {
                assertThat(rule.calls.get()).as("calls of enabled %s", rule.name).isEqualTo(1);
            } else {
                assertThat(rule.calls.get()).as("calls of disabled %s", rule.name).isZero();
                assertThat(decision.ruleResults()).noneMatch(r -> r.rule().equals(rule.name));
            }
        }

        // a left-out rule contributes 0: score equals the capped sum over injected rules only
        int expected = Math.min(100, injected.stream().filter(r -> r.fired).mapToInt(r -> r.weight).sum());
        assertThat(decision.riskScore()).isEqualTo(expected);
    }

    @Test
    void leavingOutAFiringRuleLowersScoreByItsWeight() {
        CountingRule a = new CountingRule("a", true, 20);
        CountingRule b = new CountingRule("b", true, 30);

        int both = new ScoringService(List.of(a, b), new FraudProperties()).score(REQUEST).riskScore();
        int onlyA = new ScoringService(List.of(a), new FraudProperties()).score(REQUEST).riskScore();

        assertThat(both).isEqualTo(50);
        assertThat(onlyA).isEqualTo(20);
    }
}
