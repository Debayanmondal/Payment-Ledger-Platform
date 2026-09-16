package com.bank.payment.service;

import com.bank.payment.entity.OutboxEvent;
import com.bank.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Poller service implementing the Transactional Outbox pattern.
 * Ensures zero message loss by streaming database outbox events to Kafka.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.payment-initiated:payment.initiated}")
    private String paymentInitiatedTopic;

    @Scheduled(fixedDelay = 1500)
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTopPendingEvents(
                OutboxEvent.OutboxStatus.PENDING, PageRequest.of(0, 50));

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events to publish", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            processOutboxEvent(event);
        }
    }

    private void processOutboxEvent(OutboxEvent event) {
        try {
            // Partition by aggregateId (Payment UUID) to guarantee ordering per payment
            kafkaTemplate.send(paymentInitiatedTopic, event.getAggregateId(), event.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            markEventAsPublished(event.getId());
                            log.info("Successfully published outbox event [{}] to Kafka topic [{}]",
                                    event.getId(), paymentInitiatedTopic);
                        } else {
                            markEventAsFailed(event.getId(), ex.getMessage());
                            log.error("Failed to publish outbox event [{}] to Kafka: {}",
                                    event.getId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            markEventAsFailed(event.getId(), e.getMessage());
            log.error("Unexpected error publishing outbox event [{}]: {}", event.getId(), e.getMessage());
        }
    }

    @Transactional
    public void markEventAsPublished(java.util.UUID eventId) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
            event.setProcessedAt(Instant.now());
            outboxEventRepository.save(event);
        });
    }

    @Transactional
    public void markEventAsFailed(java.util.UUID eventId, String errorMessage) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            event.setRetryCount(event.getRetryCount() + 1);
            if (event.getRetryCount() >= 5) {
                event.setStatus(OutboxEvent.OutboxStatus.FAILED);
            }
            event.setErrorMessage(errorMessage != null && errorMessage.length() > 900
                    ? errorMessage.substring(0, 900) : errorMessage);
            outboxEventRepository.save(event);
        });
    }
}
