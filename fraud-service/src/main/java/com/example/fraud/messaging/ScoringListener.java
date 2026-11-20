package com.example.fraud.messaging;

import com.example.fraud.dto.Decision;
import com.example.fraud.dto.ScoringRequest;
import com.example.fraud.dto.TransactionScoredEvent;
import com.example.fraud.service.ScoringService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumes submitted transactions, scores them and publishes the result. The scored event is
 * keyed by account id so all events of one account stay ordered on the same partition.
 * Delivery is at-least-once: a redelivered record is scored again and produces the same event.
 */
@Component
public class ScoringListener {

    private static final Logger log = LoggerFactory.getLogger(ScoringListener.class);

    private final ScoringService scoringService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final String scoredTopic;

    public ScoringListener(ScoringService scoringService, KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper, Validator validator,
                           @Value("${app.kafka.scored-topic}") String scoredTopic) {
        this.scoringService = scoringService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.scoredTopic = scoredTopic;
    }

    @KafkaListener(topics = "${app.kafka.submitted-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onSubmitted(String payload) throws JsonProcessingException {
        ScoringRequest request = objectMapper.readValue(payload, ScoringRequest.class);
        Set<ConstraintViolation<ScoringRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Invalid scoring request: " + violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", ")));
        }

        Decision decision = scoringService.score(request);
        String json = objectMapper.writeValueAsString(
                new TransactionScoredEvent(request.transaction(), decision));
        publish(request.transaction().accountId(), json);
        log.debug("Scored transaction {} riskScore={} flagged={}", request.transaction().id(),
                decision.riskScore(), decision.flagged());
    }

    /** Waits for the broker acknowledgement so a failed send triggers a redelivery. */
    private void publish(String key, String json) {
        try {
            kafkaTemplate.send(scoredTopic, key, json).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing scored event", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not publish scored event", e);
        }
    }
}
