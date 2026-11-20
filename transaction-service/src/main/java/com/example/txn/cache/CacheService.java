package com.example.txn.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cache-aside on Redis with JSON values and a TTL per entry. Redis is an optimization only:
 * any Redis or serialization error is logged and the loader runs instead, so a Redis outage
 * slows requests down but never fails them.
 */
@Component
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public CacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader) {
        return getOrLoad(key, ttl, type, loader, value -> true);
    }

    /**
     * Returns the cached value, or loads it and caches it when {@code cacheable} accepts it.
     * A null loader result is never cached.
     */
    public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader,
                           Predicate<T> cacheable) {
        T cached = read(key, type);
        if (cached != null) {
            return cached;
        }
        T value = loader.get();
        if (value != null && cacheable.test(value)) {
            write(key, value, ttl);
        }
        return value;
    }

    public void evict(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("Cache evict failed for {}: {}", key, e.toString());
        }
    }

    private <T> T read(String key, TypeReference<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Cache read failed for {}: {}", key, e.toString());
            return null;
        }
    }

    private void write(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Cache write failed for {}: {}", key, e.toString());
        }
    }
}
