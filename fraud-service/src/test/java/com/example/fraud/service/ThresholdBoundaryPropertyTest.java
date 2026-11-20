package com.example.fraud.service;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.dto.Decision;
import com.example.fraud.dto.RuleResult;
import com.example.fraud.dto.ScoringRequest;
import com.example.fraud.dto.TxDto;
import com.example.fraud.rule.FraudRule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 2: Threshold boundary. flagged == (riskScore >= T); a score of T-1 is not flagged and a score of T is.
 *
 * <p><b>Validates: Requirements 3.3, 3.4, 9.3</b>
 */
class ThresholdBoundaryPropertyTest {

    private static final long SEED = 20240602L;
    private static final int RANDOM_CASES = 200;

    private static final ScoringRequest REQUEST = new ScoringRequest(
            new TxDto("tx-1", "acc-1", new BigDecimal("100.00"), "IDR", "m-1", "RETAIL", "ID",
                    Instant.parse("2024-01-01T12:00:00Z")),
            List.of(), null);

    /** Stub rule with a fixed outcome. */
    private static FraudRule stub(String name, boolean fired, int weight) {
        return request -> new RuleResult(name, fired, fired ? weight : 0, "stub " + name);
    }

    /** Splits {@code total} into 1..4 positive parts (empty list for 0), each at most 100. */
    private static List<Integer> split(int total, Random random) {
        List<Integer> parts = new ArrayList<>();
        int remaining = total;
        int maxParts = 1 + random.nextInt(4);
        while (remaining > 0) {
            int part = parts.size() == maxParts - 1 ? remaining : 1 + random.nextInt(remaining);
            parts.add(part);
            remaining -= part;
        }
        return parts;
    }

    /** Fired stubs summing to exactly {@code score}, plus non-fired stubs that must not count. */
    private static List<FraudRule> rulesWithScore(int score, Random random) {
        List<FraudRule> rules = new ArrayList<>();
        List<Integer> parts = split(score, random);
        for (int i = 0; i < parts.size(); i++) {
            rules.add(stub("fired-" + i, true, parts.get(i)));
        }
        int silent = random.nextInt(3);
        for (int i = 0; i < silent; i++) {
            rules.add(stub("silent-" + i, false, 1 + random.nextInt(100)));
        }
        return rules;
    }

    private static ScoringService serviceWith(List<FraudRule> rules, int threshold) {
        FraudProperties props = new FraudProperties();
        props.setFlagThreshold(threshold);
        return new ScoringService(rules, props);
    }

    static Stream<Arguments> fixedTable() {
        // threshold, score
        return Stream.of(
                Arguments.of(60, 59), Arguments.of(60, 60), Arguments.of(60, 61),
                Arguments.of(1, 0), Arguments.of(1, 1),
                Arguments.of(100, 99), Arguments.of(100, 100),
                Arguments.of(0, 0),
                Arguments.of(50, 0), Arguments.of(50, 49), Arguments.of(50, 50), Arguments.of(50, 100));
    }

    static Stream<Arguments> randomCases() {
        Random random = new Random(SEED);
        return IntStream.range(0, RANDOM_CASES)
                .mapToObj(i -> {
                    int threshold = random.nextInt(101);
                    int score = random.nextInt(101);
                    return Arguments.of(threshold, score);
                });
    }

    @ParameterizedTest(name = "T={0}, score={1}")
    @MethodSource("fixedTable")
    void flaggedIffScoreAtLeastThreshold_fixedTable(int threshold, int score) {
        assertFlaggedIffAtLeastThreshold(threshold, score, new Random(threshold * 1000L + score));
    }

    @ParameterizedTest(name = "T={0}, score={1}")
    @MethodSource("randomCases")
    void flaggedIffScoreAtLeastThreshold_seededRandom(int threshold, int score) {
        assertFlaggedIffAtLeastThreshold(threshold, score, new Random(SEED + threshold * 1000L + score));
    }

    @Test
    void scoreOneBelowThresholdIsNotFlaggedAndAtThresholdIsFlagged_forEveryThreshold() {
        Random random = new Random(SEED);
        for (int t = 1; t <= 100; t++) {
            Decision below = serviceWith(rulesWithScore(t - 1, random), t).score(REQUEST);
            Decision at = serviceWith(rulesWithScore(t, random), t).score(REQUEST);

            assertThat(below.riskScore()).as("score below, T=%d", t).isEqualTo(t - 1);
            assertThat(below.flagged()).as("T-1 not flagged, T=%d", t).isFalse();
            assertThat(at.riskScore()).as("score at, T=%d", t).isEqualTo(t);
            assertThat(at.flagged()).as("T flagged, T=%d", t).isTrue();
        }
    }

    private static void assertFlaggedIffAtLeastThreshold(int threshold, int score, Random random) {
        Decision decision = serviceWith(rulesWithScore(score, random), threshold).score(REQUEST);

        assertThat(decision.riskScore()).isEqualTo(score);
        assertThat(decision.flagged()).isEqualTo(score >= threshold);
    }
}
