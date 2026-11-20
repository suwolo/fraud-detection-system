package com.example.txn.messaging;

import com.example.txn.dto.ScoringRequest;
import com.example.txn.exception.FraudUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes submitted transactions for scoring. The message key is the account id, so one
 * account's transactions are processed in order. Waits for the broker acknowledgement and
 * reports any failure as {@link FraudUnavailableException}.
 */
@Component
public class TransactionEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String submittedTopic;
    private final long sendTimeoutMs;

    public TransactionEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                     @Value("${app.kafka.submitted-topic}") String submittedTopic,
                                     @Value("${app.kafka.send-timeout-ms:5000}") long sendTimeoutMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.submittedTopic = submittedTopic;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public void publishSubmitted(ScoringRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            kafkaTemplate.send(submittedTopic, request.transaction().accountId(), json)
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize scoring request", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FraudUnavailableException("Interrupted while queuing transaction", e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new FraudUnavailableException("Could not queue transaction for scoring: " + e.getMessage(), e);
        }
    }
}
