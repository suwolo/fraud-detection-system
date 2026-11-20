package com.example.txn.service;

import com.example.txn.cache.CacheService;
import com.example.txn.repository.TransactionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;

import com.example.txn.dto.CategoryStats;
import com.example.txn.dto.StatisticsResponse;
import com.example.txn.dto.Tx;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Stream-based aggregation of an account's transactions. Money is BigDecimal, HALF_UP, 2 decimals. */
@Service
public class StatisticsService {

    private static final int SCALE = 2;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    static final Duration TTL = Duration.ofMinutes(2);
    private static final TypeReference<StatisticsResponse> RESPONSE = new TypeReference<>() { };

    private final TransactionRepository repository;
    private final CacheService cache;

    public StatisticsService(TransactionRepository repository, CacheService cache) {
        this.repository = repository;
        this.cache = cache;
    }

    /**
     * Loads the account's transactions with from <= eventTime <= to and aggregates them. The result
     * is cached for two minutes, so new transactions can take that long to show up.
     */
    public StatisticsResponse statistics(String accountId, Instant from, Instant to) {
        return cache.getOrLoad("stats:" + accountId + ":" + from + ":" + to, TTL, RESPONSE,
                () -> compute(repository.findForStatistics(accountId, from, to)));
    }

    /** Pure computation, no I/O. */
    public StatisticsResponse compute(List<Tx> txs) {
        long count = txs.size();
        if (count == 0) {
            return new StatisticsResponse(0L, ZERO, null, null, null, new TreeMap<>());
        }
        BigDecimal sum = txs.stream().map(Tx::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal min = txs.stream().map(Tx::amount).min(Comparator.naturalOrder()).orElse(null);
        BigDecimal max = txs.stream().map(Tx::amount).max(Comparator.naturalOrder()).orElse(null);
        TreeMap<String, CategoryStats> byCategory = txs.stream().collect(
                Collectors.groupingBy(Tx::merchantCategory, TreeMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), StatisticsService::toCategoryStats)));
        return new StatisticsResponse(count, round(sum), average(sum, count), round(min), round(max), byCategory);
    }

    private static CategoryStats toCategoryStats(List<Tx> group) {
        BigDecimal total = group.stream().map(Tx::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CategoryStats(group.size(), round(total), average(total, group.size()));
    }

    private static BigDecimal average(BigDecimal sum, long count) {
        return sum.divide(BigDecimal.valueOf(count), SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
