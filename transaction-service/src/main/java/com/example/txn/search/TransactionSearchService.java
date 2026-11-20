package com.example.txn.search;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.json.JsonData;
import com.example.txn.dto.PageResponse;
import com.example.txn.dto.TransactionResponse;
import com.example.txn.dto.TransactionStatus;
import com.example.txn.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch read model of scored transactions. PostgreSQL stays the system of record: the
 * index is filled from the scored topic and can be rebuilt from the database with
 * {@link #reindexAll()}.
 */
@Service
public class TransactionSearchService {

    private static final Logger log = LoggerFactory.getLogger(TransactionSearchService.class);
    private static final int REINDEX_BATCH = 500;

    private final ElasticsearchOperations operations;
    private final TransactionRepository repository;
    private final AtomicBoolean indexReady = new AtomicBoolean(false);

    public TransactionSearchService(ElasticsearchOperations operations, TransactionRepository repository) {
        this.operations = operations;
        this.repository = repository;
    }

    /** Indexes one scored transaction. Saving the same id again overwrites it, so this is idempotent. */
    public void index(TransactionResponse response) {
        ensureIndex();
        operations.save(TransactionDocument.from(response));
    }

    /**
     * Full-text and filtered search, newest first. Every parameter is optional. {@code query}
     * matches merchant id, merchant category and the explanations of the fired rules.
     */
    public PageResponse<TransactionSearchHit> search(SearchCriteria c, int page, int size) {
        ensureIndex();
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (c.query() != null && !c.query().isBlank()) {
            bool.must(m -> m.multiMatch(mm -> mm.query(c.query())
                    .fields("merchantId", "merchantCategory", "explanations")));
        }
        term(bool, "accountId", c.accountId());
        term(bool, "merchantId", c.merchantId());
        term(bool, "country", c.country());
        term(bool, "status", c.status());
        term(bool, "firedRules", c.rule());
        if (c.minAmount() != null || c.maxAmount() != null) {
            bool.filter(f -> f.range(r -> {
                r.field("amount");
                if (c.minAmount() != null) {
                    r.gte(JsonData.of(c.minAmount()));
                }
                if (c.maxAmount() != null) {
                    r.lte(JsonData.of(c.maxAmount()));
                }
                return r;
            }));
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(bool.build()))
                .withSort(s -> s.field(f -> f.field("eventTime").order(SortOrder.Desc)))
                .withPageable(PageRequest.of(page, size))
                .withTrackTotalHits(true)
                .build();
        SearchHits<TransactionDocument> hits = operations.search(query, TransactionDocument.class);

        List<TransactionSearchHit> content = hits.getSearchHits().stream()
                .map(h -> h.getContent().toHit())
                .toList();
        long total = hits.getTotalHits();
        return new PageResponse<>(content, page, size, total, (int) ((total + size - 1) / size));
    }

    /** Rebuilds the index from PostgreSQL (scored transactions only). Returns the number indexed. */
    public long reindexAll() {
        ensureIndex();
        long indexed = 0;
        int page = 0;
        PageResponse<TransactionResponse> batch;
        do {
            batch = repository.findPage(null, null, page++, REINDEX_BATCH);
            List<TransactionDocument> docs = batch.content().stream()
                    .filter(t -> t.status() != TransactionStatus.RECEIVED)
                    .map(TransactionDocument::from)
                    .toList();
            if (!docs.isEmpty()) {
                operations.save(docs);
                indexed += docs.size();
            }
        } while (page < batch.totalPages());
        log.info("Reindexed {} transactions", indexed);
        return indexed;
    }

    private static void term(BoolQuery.Builder bool, String field, String value) {
        if (value != null && !value.isBlank()) {
            bool.filter(f -> f.term(t -> t.field(field).value(value)));
        }
    }

    /** Creates the index with its mapping on first use. Retried on every call until it succeeds. */
    private void ensureIndex() {
        if (indexReady.get()) {
            return;
        }
        IndexOperations indexOps = operations.indexOps(TransactionDocument.class);
        if (!indexOps.exists()) {
            indexOps.createWithMapping();
        }
        indexReady.set(true);
    }

    /** Search filters. Null or blank values are ignored. */
    public record SearchCriteria(String query, String accountId, String merchantId, String country,
                                 String status, String rule, BigDecimal minAmount, BigDecimal maxAmount) {
    }
}
