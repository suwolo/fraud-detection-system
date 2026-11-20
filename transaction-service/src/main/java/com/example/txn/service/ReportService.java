package com.example.txn.service;

import com.example.txn.cache.CacheService;
import com.example.txn.dto.FlaggedAccount;
import com.example.txn.dto.RiskyMerchant;
import com.example.txn.repository.TransactionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Report queries behind a short Redis cache. Reports aggregate the whole table, so they are
 * cached for a minute instead of being invalidated on every decision.
 */
@Service
public class ReportService {

    static final Duration TTL = Duration.ofMinutes(1);

    private static final TypeReference<List<FlaggedAccount>> FLAGGED = new TypeReference<>() { };
    private static final TypeReference<List<RiskyMerchant>> RISKY = new TypeReference<>() { };

    private final TransactionRepository repository;
    private final CacheService cache;

    public ReportService(TransactionRepository repository, CacheService cache) {
        this.repository = repository;
        this.cache = cache;
    }

    public List<FlaggedAccount> flaggedAccounts(int minCount) {
        return cache.getOrLoad("report:flagged-accounts:" + minCount, TTL, FLAGGED,
                () -> repository.findFlaggedAccounts(minCount));
    }

    public List<RiskyMerchant> riskyMerchants(LocalDate from, LocalDate to) {
        return cache.getOrLoad("report:risky-merchants:" + from + ":" + to, TTL, RISKY,
                () -> repository.findRiskyMerchants(from, to));
    }
}
