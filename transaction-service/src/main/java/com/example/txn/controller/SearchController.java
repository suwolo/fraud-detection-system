package com.example.txn.controller;

import com.example.txn.dto.PageResponse;
import com.example.txn.exception.InvalidParameterException;
import com.example.txn.search.TransactionSearchHit;
import com.example.txn.search.TransactionSearchService;
import com.example.txn.search.TransactionSearchService.SearchCriteria;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Elasticsearch-backed search over scored transactions. */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> STATUSES = Set.of("APPROVED", "FLAGGED");

    private final TransactionSearchService searchService;

    public SearchController(TransactionSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/transactions")
    public PageResponse<TransactionSearchHit> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String rule,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new InvalidParameterException("page", "page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidParameterException("size", "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (status != null && !STATUSES.contains(status)) {
            throw new InvalidParameterException("status", "status must be one of APPROVED, FLAGGED");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new InvalidParameterException("minAmount", "minAmount must not be greater than maxAmount");
        }
        return searchService.search(
                new SearchCriteria(q, accountId, merchantId, country, status, rule, minAmount, maxAmount),
                page, size);
    }

    /** Rebuilds the index from PostgreSQL. Needed once for the seeded data, which never passed through Kafka. */
    @PostMapping("/reindex")
    public Map<String, Long> reindex() {
        return Map.of("indexed", searchService.reindexAll());
    }
}
