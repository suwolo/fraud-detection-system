package com.example.txn.streams;

import com.example.txn.dto.LiveRiskyMerchant;
import com.example.txn.exception.StreamsNotReadyException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Service;

/** Interactive queries against the local window store (this instance's partitions only). */
@Service
public class MerchantFlagQueryService {

    private static final int TOP = 10;

    private final StreamsBuilderFactoryBean factoryBean;

    public MerchantFlagQueryService(StreamsBuilderFactoryBean factoryBean) {
        this.factoryBean = factoryBean;
    }

    /** Merchants with the most flagged transactions in windows that overlap the last {@code minutes}. */
    public List<LiveRiskyMerchant> topMerchants(int minutes) {
        KafkaStreams streams = factoryBean.getKafkaStreams();
        if (streams == null || streams.state() != KafkaStreams.State.RUNNING) {
            throw new StreamsNotReadyException("Stream processing is not running yet");
        }
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofMinutes(minutes));
        Map<String, Long> totals = new HashMap<>();
        try {
            ReadOnlyWindowStore<String, Long> store = streams.store(StoreQueryParameters
                    .fromNameAndType(MerchantFlagTopology.STORE, QueryableStoreTypes.windowStore()));
            try (KeyValueIterator<Windowed<String>, Long> it = store.fetchAll(from, to)) {
                List<KeyValue<Windowed<String>, Long>> rows = new ArrayList<>();
                it.forEachRemaining(rows::add);
                rows.forEach(kv -> totals.merge(kv.key.key(), kv.value, Long::sum));
            }
        } catch (InvalidStateStoreException e) {
            throw new StreamsNotReadyException("Stream state store is not available yet", e);
        }
        return totals.entrySet().stream()
                .map(e -> new LiveRiskyMerchant(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(LiveRiskyMerchant::flaggedCount).reversed()
                        .thenComparing(LiveRiskyMerchant::merchantId))
                .limit(TOP)
                .toList();
    }
}
