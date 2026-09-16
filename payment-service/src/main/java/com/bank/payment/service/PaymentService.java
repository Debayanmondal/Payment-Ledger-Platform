package com.bank.payment.service;

import com.bank.common.dto.PaymentStatus;
import com.bank.common.event.PaymentInitiatedEvent;
import com.bank.payment.dto.PaymentRequest;
import com.bank.payment.dto.PaymentResponse;
import com.bank.payment.entity.OutboxEvent;
import com.bank.payment.entity.Payment;
import com.bank.payment.exception.AccountLockedException;
import com.bank.payment.repository.OutboxEventRepository;
import com.bank.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IdempotencyService idempotencyService;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    @Value("${app.lock.wait-time-seconds:5}")
    private long lockWaitTime;

    @Value("${app.lock.lease-time-seconds:10}")
    private long lockLeaseTime;

    /**
     * Process a payment with distributed lock and transactional outbox.
     */
    public PaymentResponse initiatePayment(String idempotencyKey, PaymentRequest request) {
        // Step 1: Idempotency Cache check
        Optional<PaymentResponse> cached = idempotencyService.getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            log.info("Idempotent hit for key: {}. Returning cached payment.", idempotencyKey);
            return cached.get();
        }

        // Check if already persisted in DB (e.g. if Redis key expired or eviction occurred)
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            Payment p = existingPayment.get();
            PaymentResponse response = mapToResponse(p, true);
            idempotencyService.cacheResponse(idempotencyKey, response);
            return response;
        }

        // Step 2: Acquire Distributed Lock on Source Account to prevent race conditions & double-spend
        String lockKey = "lock:account:" + request.getSourceAccountId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(lockWaitTime, lockLeaseTime, TimeUnit.SECONDS);
            if (!acquired) {
                throw new AccountLockedException("Source account " + request.getSourceAccountId() + " is currently processing another transaction. Try again.");
            }

            log.info("Acquired distributed lock for account: {}", request.getSourceAccountId());

            // Step 3: Atomic database transaction (Payment + Outbox Event)
            PaymentResponse response = createPaymentAndOutbox(idempotencyKey, request);

            // Step 4: Cache response for subsequent idempotent requests
            idempotencyService.cacheResponse(idempotencyKey, response);

            return response;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while waiting for distributed lock", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("Released distributed lock for account: {}", request.getSourceAccountId());
            }
        }
    }

    @Transactional
    public PaymentResponse createPaymentAndOutbox(String idempotencyKey, PaymentRequest request) {
        // Create Payment entity
        Payment payment = Payment.builder()
                .idempotencyKey(idempotencyKey)
                .sourceAccountId(request.getSourceAccountId())
                .destinationAccountId(request.getDestinationAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(PaymentStatus.PENDING)
                .description(request.getDescription())
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        // Prepare Kafka Event payload
        PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(savedPayment.getPaymentId())
                .idempotencyKey(idempotencyKey)
                .sourceAccountId(request.getSourceAccountId())
                .destinationAccountId(request.getDestinationAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .timestamp(Instant.now())
                .build();

        // Save to Outbox table in the SAME transaction
        try {
            String payloadJson = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("PAYMENT")
                    .aggregateId(savedPayment.getPaymentId().toString())
                    .eventType("PaymentInitiatedEvent")
                    .payload(payloadJson)
                    .status(OutboxEvent.OutboxStatus.PENDING)
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.info("Stored Payment [{}] and OutboxEvent [{}] atomically in database", savedPayment.getPaymentId(), outboxEvent.getId());
        } catch (Exception e) {
            log.error("Failed to serialize outbox event payload for payment: {}", savedPayment.getPaymentId(), e);
            throw new RuntimeException("Failed to register outbox event", e);
        }

        return mapToResponse(savedPayment, false);
    }

    public PaymentResponse getPaymentById(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
        return mapToResponse(payment, false);
    }

    private PaymentResponse mapToResponse(Payment payment, boolean isReplay) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .idempotencyKey(payment.getIdempotencyKey())
                .sourceAccountId(payment.getSourceAccountId())
                .destinationAccountId(payment.getDestinationAccountId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .message(isReplay ? "Payment previously accepted (Idempotent Replay)" : "Payment accepted and queued for ledger processing")
                .createdAt(payment.getCreatedAt())
                .idempotentReplay(isReplay)
                .build();
    }
}
