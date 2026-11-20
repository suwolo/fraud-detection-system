package com.example.txn.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Topics and error handling. Failing records are retried, then published to {@code <topic>.DLT}. */
@Configuration
public class KafkaConfig {

    @Bean
    NewTopic submittedTopic(@Value("${app.kafka.submitted-topic}") String name,
                            @Value("${app.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic submittedDeadLetterTopic(@Value("${app.kafka.submitted-topic}") String name,
                                      @Value("${app.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(name + ".DLT").partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic scoredTopic(@Value("${app.kafka.scored-topic}") String name,
                         @Value("${app.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic scoredDeadLetterTopic(@Value("${app.kafka.scored-topic}") String name,
                                   @Value("${app.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(name + ".DLT").partitions(partitions).replicas(1).build();
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template,
                                         @Value("${app.kafka.retry-attempts:3}") long attempts,
                                         @Value("${app.kafka.retry-interval-ms:1000}") long intervalMs) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(template), new FixedBackOff(intervalMs, attempts));
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }
}
