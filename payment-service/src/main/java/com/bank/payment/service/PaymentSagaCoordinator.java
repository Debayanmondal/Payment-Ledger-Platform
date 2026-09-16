package com.bank.payment.service;

import com.bank.common.dto.PaymentStatus;
import com.bank.common.event.LedgerProcessedEvent;
import com.bank.payment.entity.Payment;
import com.bank.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Saga Coordinator listening for ledger confirmation to finalize payment lifecycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSagaCoordinator {

    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${app.kafka.topics.ledger-processed:ledger.processed}", groupId = "payment-saga-group")
    @Transactional
    public void handleLedgerResponse(String eventPayload) {
        try {
            LedgerProcessedEvent event = objectMapper.readValue(eventPayload, LedgerProcessedEvent.class);
            log.info("Received LedgerProcessedEvent for payment [{}]: status={}", event.getPaymentId(), event.getStatus());

            Optional<Payment> optionalPayment = paymentRepository.findById(event.getPaymentId());
            if (optionalPayment.isEmpty()) {
                log.warn("Payment [{}] not found for ledger callback", event.getPaymentId());
                return;
            }

            Payment payment = optionalPayment.get();
            if ("SUCCESS".equalsIgnoreCase(event.getStatus())) {
                payment.setStatus(PaymentStatus.COMPLETED);
                log.info("Saga Complete: Payment [{}] transitioned to COMPLETED. JournalEntryId: {}",
                        payment.getPaymentId(), event.getJournalEntryId());
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setDescription("Failed in Ledger: " + event.getFailureReason());
                log.warn("Saga Compensation: Payment [{}] marked as FAILED due to: {}",
                        payment.getPaymentId(), event.getFailureReason());
            }

            paymentRepository.save(payment);

        } catch (Exception e) {
            log.error("Error processing LedgerProcessedEvent message: {}", eventPayload, e);
        }
    }
}
