package com.example.txn.service;

import com.example.txn.cache.CacheService;
import com.example.txn.repository.TransactionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.txn.dto.CategoryStats;
import com.example.txn.dto.StatisticsResponse;
import com.example.txn.dto.Tx;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatisticsServiceTest {

    private final StatisticsService service = new StatisticsService(mock(TransactionRepository.class), mock(CacheService.class));

    private static Tx tx(String amount, String category) {
        return new Tx("id", "ACC-1", new BigDecimal(amount), "USD", "M-1", category, "ID",
                Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    void nonEmptyListComputesSumAverageMinMaxAndPerCategory() {
        StatisticsResponse r = service.compute(List.of(
                tx("10.00", "FOOD"), tx("10.01", "FOOD"), tx("100.00", "TRAVEL")));

        assertThat(r.count()).isEqualTo(3L);
        assertThat(r.sum()).isEqualTo(new BigDecimal("120.01"));
        assertThat(r.average()).isEqualTo(new BigDecimal("40.00"));
        assertThat(r.min()).isEqualTo(new BigDecimal("10.00"));
        assertThat(r.max()).isEqualTo(new BigDecimal("100.00"));

        assertThat(r.byCategory()).containsOnlyKeys("FOOD", "TRAVEL");
        CategoryStats food = r.byCategory().get("FOOD");
        assertThat(food.count()).isEqualTo(2L);
        assertThat(food.total()).isEqualTo(new BigDecimal("20.01"));
        // 20.01 / 2 = 10.005 -> 10.01 (half-up)
        assertThat(food.average()).isEqualTo(new BigDecimal("10.01"));
        CategoryStats travel = r.byCategory().get("TRAVEL");
        assertThat(travel.count()).isEqualTo(1L);
        assertThat(travel.total()).isEqualTo(new BigDecimal("100.00"));
        assertThat(travel.average()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void averageRoundsHalfUp() {
        StatisticsResponse r = service.compute(List.of(tx("0.01", "A"), tx("0.02", "A")));

        // 0.03 / 2 = 0.015 -> 0.02
        assertThat(r.average()).isEqualTo(new BigDecimal("0.02"));
    }

    @Test
    void emptyListYieldsZeroCountZeroSumNullsAndEmptyMap() {
        StatisticsResponse r = service.compute(List.of());

        assertThat(r.count()).isZero();
        assertThat(r.sum()).isEqualTo(new BigDecimal("0.00"));
        assertThat(r.sum().scale()).isEqualTo(2);
        assertThat(r.average()).isNull();
        assertThat(r.min()).isNull();
        assertThat(r.max()).isNull();
        assertThat(r.byCategory()).isEmpty();
    }
}
