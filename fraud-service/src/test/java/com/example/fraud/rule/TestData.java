package com.example.fraud.rule;

import com.example.fraud.dto.PrevTx;
import com.example.fraud.dto.ScoringRequest;
import com.example.fraud.dto.TxDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/** Builders for rule unit tests. */
final class TestData {
    static final Instant NOON = Instant.parse("2024-01-15T12:00:00Z");

    private TestData() {
    }

    static TxDto tx(BigDecimal amount, String country, Instant time) {
        return new TxDto("tx-1", "acc-1", amount, "IDR", "m-1", "RETAIL", country, time);
    }

    static ScoringRequest request(TxDto tx, List<TxDto> recent, PrevTx previous) {
        return new ScoringRequest(tx, recent, previous);
    }

    static ScoringRequest request(TxDto tx) {
        return request(tx, Collections.emptyList(), null);
    }

    static List<TxDto> recent(int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> tx(new BigDecimal("1000"), "ID", NOON.minusSeconds(60L * (i + 1))))
                .toList();
    }
}
