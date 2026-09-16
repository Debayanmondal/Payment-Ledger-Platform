package com.bank.payment.service;

import com.bank.payment.dto.PaymentResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    @Value("${app.idempotency.ttl-seconds:86400}")
    private long ttlSeconds;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:key:";

    /**
     * Checks if a completed response exists for the given idempotency key.
     */
    public Optional<PaymentResponse> getCachedResponse(String idempotencyKey) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(IDEMPOTENCY_PREFIX + idempotencyKey);
            String cachedJson = bucket.get();
            if (cachedJson != null) {
                PaymentResponse response = objectMapper.readValue(cachedJson, PaymentResponse.class);
                response.setIdempotentReplay(true);
                return Optional.of(response);
            }
        } catch (Exception e) {
            log.error("Error reading idempotency key {} from Redis: {}", idempotencyKey, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Caches the completed payment response under the idempotency key.
     */
    public void cacheResponse(String idempotencyKey, PaymentResponse response) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(IDEMPOTENCY_PREFIX + idempotencyKey);
            String json = objectMapper.writeValueAsString(response);
            bucket.set(json, Duration.ofSeconds(ttlSeconds));
            log.info("Cached idempotent response for key: {}", idempotencyKey);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize response for idempotency key: {}", idempotencyKey, e);
        }
    }
}
