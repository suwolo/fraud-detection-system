package com.example.fraud.rule;

import com.example.fraud.dto.ScoringRequest;
import com.example.fraud.dto.TxDto;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Sliding-window counter kept in a Redis sorted set per account: member = transaction id,
 * score = event time in epoch milliseconds. Adding the same transaction twice is a no-op, so
 * Kafka redeliveries do not inflate the count. Entries older than the retention period are
 * trimmed on every write and the key expires when the account goes quiet.
 */
@Component
@ConditionalOnProperty(name = "app.velocity.store", havingValue = "redis")
public class RedisVelocityCounter implements VelocityCounter {

    static final String KEY_PREFIX = "velocity:";
    static final Duration RETENTION = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public RedisVelocityCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public int countInWindow(ScoringRequest request, int windowMinutes) {
        TxDto tx = request.transaction();
        String key = KEY_PREFIX + tx.accountId();
        long eventMillis = tx.eventTime().toEpochMilli();
        long windowStart = eventMillis - Duration.ofMinutes(windowMinutes).toMillis();

        var zset = redis.opsForZSet();
        zset.add(key, tx.id(), eventMillis);
        zset.removeRangeByScore(key, Double.NEGATIVE_INFINITY, eventMillis - RETENTION.toMillis());
        redis.expire(key, RETENTION);
        Long count = zset.count(key, windowStart, eventMillis);
        return count == null ? 1 : count.intValue();
    }
}
