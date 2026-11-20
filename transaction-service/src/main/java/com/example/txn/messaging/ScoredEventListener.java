package com.example.txn.messaging;

import com.example.txn.dto.TransactionScoredEvent;
import com.example.txn.search.TransactionSearchService;
import com.example.txn.service.TransactionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumers of the scored topic. Each has its own consumer group, so storing the decision in
 * PostgreSQL and indexing it in Elasticsearch progress and fail independently. Both operations
 * are idempotent, which makes redelivery safe.
 */
@Component
public class ScoredEventListener {

    private final ObjectMapper objectMapper;
    private final TransactionService transactionService;
    private final TransactionSearchService searchService;

    public ScoredEventListener(ObjectMapper objectMapper, TransactionService transactionService,
                               TransactionSearchService searchService) {
        this.objectMapper = objectMapper;
        this.transactionService = transactionService;
        this.searchService = searchService;
    }

    @KafkaListener(topics = "${app.kafka.scored-topic}", groupId = "transaction-service-decision")
    public void storeDecision(String payload) throws JsonProcessingException {
        transactionService.applyDecision(objectMapper.readValue(payload, TransactionScoredEvent.class));
    }

    @KafkaListener(topics = "${app.kafka.scored-topic}", groupId = "transaction-service-indexer")
    public void indexTransaction(String payload) throws JsonProcessingException {
        searchService.index(objectMapper.readValue(payload, TransactionScoredEvent.class).toResponse());
    }
}
