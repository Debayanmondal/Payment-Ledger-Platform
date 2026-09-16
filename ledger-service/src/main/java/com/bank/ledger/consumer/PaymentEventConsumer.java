package com.bank.ledger.consumer;

import com.bank.common.event.PaymentInitiatedEvent;
import com.bank.ledger.service.LedgerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.kafka.topics.payment-initiated:payment.initiated}", groupId = "ledger-processing-group")
    public void consumePaymentInitiatedEvent(String payload) {
        log.info("Received Kafka message on payment.initiated: {}", payload);

        try {
            // Check for simulated poison pill for DLQ testing
            if (payload.contains("SIMULATE_DLQ_ERROR")) {
                throw new IllegalArgumentException("Simulated DLQ error: Corrupted or unprocessable payload detected!");
            }

            PaymentInitiatedEvent event = objectMapper.readValue(payload, PaymentInitiatedEvent.class);
            ledgerService.processPaymentEvent(event);

        } catch (Exception e) {
            log.error("Failed to process payment event. Error will trigger DLQ recoverer: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
