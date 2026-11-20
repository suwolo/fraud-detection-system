package com.example.txn.service;

import com.example.txn.cache.CacheService;
import com.example.txn.dto.CreateTransactionRequest;
import com.example.txn.dto.PageResponse;
import com.example.txn.dto.ScoringRequest;
import com.example.txn.dto.TransactionResponse;
import com.example.txn.dto.TransactionScoredEvent;
import com.example.txn.dto.TransactionStatus;
import com.example.txn.dto.Tx;
import com.example.txn.dto.TxDto;
import com.example.txn.exception.InvalidParameterException;
import com.example.txn.exception.NotFoundException;
import com.example.txn.messaging.TransactionEventPublisher;
import com.example.txn.repository.TransactionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates submit, get and list. Submit is asynchronous: it stores the row as RECEIVED and
 * publishes it to Kafka. The decision arrives later on the scored topic and is applied by
 * {@link #applyDecision}. Deliberately not @Transactional: if the transaction cannot be queued,
 * the row stays RECEIVED.
 */
@Service
public class TransactionService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    static final Duration ACCOUNT_TTL = Duration.ofHours(1);
    static final Duration TRANSACTION_TTL = Duration.ofMinutes(10);

    private static final TypeReference<Boolean> BOOLEAN = new TypeReference<>() { };
    private static final TypeReference<TransactionResponse> RESPONSE = new TypeReference<>() { };

    private final TransactionRepository repository;
    private final TransactionEventPublisher publisher;
    private final CacheService cache;
    private final int historyWindowMinutes;

    public TransactionService(TransactionRepository repository, TransactionEventPublisher publisher,
                              CacheService cache,
                              @Value("${fraud.history-window-minutes:10}") int historyWindowMinutes) {
        this.repository = repository;
        this.publisher = publisher;
        this.cache = cache;
        this.historyWindowMinutes = historyWindowMinutes;
    }

    /** Cached for an hour. Only a positive answer is cached, so a newly created account is found at once. */
    public boolean accountExists(String accountId) {
        return Boolean.TRUE.equals(cache.getOrLoad("account:" + accountId, ACCOUNT_TTL, BOOLEAN,
                () -> repository.accountExists(accountId), Boolean::booleanValue));
    }

    /** Stores the transaction as RECEIVED and queues it for scoring. */
    public TransactionResponse submit(CreateTransactionRequest request) {
        if (!accountExists(request.accountId())) {
            throw new NotFoundException("Account not found: " + request.accountId());
        }
        Tx tx = new Tx(UUID.randomUUID().toString(), request.accountId(), request.amount(),
                request.currency(), request.merchantId(), request.merchantCategory(),
                request.country(), request.eventTime());
        repository.insert(tx);

        List<TxDto> recent = repository
                .findRecent(tx.accountId(), tx.eventTime(), historyWindowMinutes, tx.id())
                .stream().map(TransactionService::toDto).toList();
        var previous = repository.findPrevious(tx.accountId(), tx.id()).orElse(null);

        publisher.publishSubmitted(new ScoringRequest(toDto(tx), recent, previous));

        return new TransactionResponse(tx.id(), tx.accountId(), tx.amount(), tx.currency(), tx.merchantId(),
                tx.merchantCategory(), tx.country(), tx.eventTime(), TransactionStatus.RECEIVED, null, null);
    }

    /** Stores the fraud decision and drops the cached copy. Idempotent, so Kafka redelivery is safe. */
    public void applyDecision(TransactionScoredEvent event) {
        repository.updateDecision(event.transaction().id(), event.status(), event.decision().riskScore(),
                event.decision().ruleResults());
        cache.evict(transactionKey(event.transaction().id()));
    }

    /** Cached for ten minutes once a decision exists. A RECEIVED row is never cached. */
    public TransactionResponse getById(String id) {
        TransactionResponse found = cache.getOrLoad(transactionKey(id), TRANSACTION_TTL, RESPONSE,
                () -> repository.findById(id).orElse(null),
                r -> r.status() != TransactionStatus.RECEIVED);
        if (found == null) {
            throw new NotFoundException("Transaction not found: " + id);
        }
        return found;
    }

    public PageResponse<TransactionResponse> list(String accountId, String status, Integer page, Integer size) {
        int effectivePage = page == null ? 0 : page;
        int effectiveSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (effectivePage < 0) {
            throw new InvalidParameterException("page", "page must be >= 0");
        }
        if (effectiveSize < 1 || effectiveSize > MAX_PAGE_SIZE) {
            throw new InvalidParameterException("size", "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        TransactionStatus parsed = null;
        if (status != null) {
            try {
                parsed = TransactionStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new InvalidParameterException("status",
                        "status must be one of RECEIVED, APPROVED, FLAGGED");
            }
        }
        return repository.findPage(accountId, parsed, effectivePage, effectiveSize);
    }

    private static String transactionKey(String id) {
        return "txn:" + id;
    }

    private static TxDto toDto(Tx t) {
        return new TxDto(t.id(), t.accountId(), t.amount(), t.currency(), t.merchantId(),
                t.merchantCategory(), t.country(), t.eventTime());
    }
}
