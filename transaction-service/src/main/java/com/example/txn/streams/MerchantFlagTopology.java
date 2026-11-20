package com.example.txn.streams;

import com.example.txn.dto.TransactionScoredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;

/**
 * Kafka Streams topology over the scored topic: counts flagged transactions per merchant in
 * tumbling windows. The counts live in the local window store {@value #STORE} and are served
 * by {@link MerchantFlagQueryService}.
 *
 * <p>Windows use the record timestamp, which is the time the scored event was produced, so the
 * counts describe live traffic and not the (possibly historical) event time of the payment.
 */
@Configuration
@EnableKafkaStreams
public class MerchantFlagTopology {

    public static final String STORE = "flagged-by-merchant";

    private static final Logger log = LoggerFactory.getLogger(MerchantFlagTopology.class);

    @Bean
    public KStream<String, String> flaggedByMerchant(StreamsBuilder builder, ObjectMapper objectMapper,
            @Value("${app.kafka.scored-topic}") String scoredTopic,
            @Value("${app.streams.window-minutes:5}") long windowMinutes) {
        KStream<String, String> scored = builder.stream(scoredTopic,
                Consumed.with(Serdes.String(), Serdes.String()));

        scored.mapValues(payload -> parse(objectMapper, payload))
                .filter((key, event) -> event != null && event.decision() != null && event.decision().flagged())
                .map((key, event) -> KeyValue.pair(event.transaction().merchantId(), event.transaction().id()))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(windowMinutes)))
                .count(Materialized.as(STORE));
        return scored;
    }

    @Bean
    public StreamsBuilderFactoryBeanConfigurer streamsRecovery() {
        return factoryBean -> factoryBean.setStreamsUncaughtExceptionHandler(
                e -> {
                    log.error("Kafka Streams thread failed, replacing it", e);
                    return org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
                            .StreamThreadExceptionResponse.REPLACE_THREAD;
                });
    }

    private static TransactionScoredEvent parse(ObjectMapper objectMapper, String payload) {
        try {
            return objectMapper.readValue(payload, TransactionScoredEvent.class);
        } catch (Exception e) {
            log.warn("Skipping unreadable scored event: {}", e.toString());
            return null;
        }
    }
}
