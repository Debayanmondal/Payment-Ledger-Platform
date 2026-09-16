package com.bank.ledger.service;

import com.bank.common.event.LedgerProcessedEvent;
import com.bank.common.event.PaymentInitiatedEvent;
import com.bank.ledger.dto.JournalEntryResponse;
import com.bank.ledger.entity.Account;
import com.bank.ledger.entity.EntryType;
import com.bank.ledger.entity.JournalEntry;
import com.bank.ledger.entity.JournalLine;
import com.bank.ledger.repository.AccountRepository;
import com.bank.ledger.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.ledger-processed:ledger.processed}")
    private String ledgerProcessedTopic;

    @Transactional
    public void processPaymentEvent(PaymentInitiatedEvent event) {
        log.info("Processing ledger posting for Payment ID: {}", event.getPaymentId());

        // Step 1: Idempotency check on Journal - has this payment already been booked?
        Optional<JournalEntry> existingEntry = journalEntryRepository.findByReferenceId(event.getPaymentId());
        if (existingEntry.isPresent()) {
            log.warn("Journal entry already exists for Payment ID: {}. Skipping duplicate booking.", event.getPaymentId());
            return;
        }

        // Step 2: Validate accounts
        Optional<Account> sourceOpt = accountRepository.findByAccountId(event.getSourceAccountId());
        Optional<Account> destOpt = accountRepository.findByAccountId(event.getDestinationAccountId());

        if (sourceOpt.isEmpty()) {
            notifyFailure(event.getPaymentId(), "Source account " + event.getSourceAccountId() + " not found");
            return;
        }

        if (destOpt.isEmpty()) {
            notifyFailure(event.getPaymentId(), "Destination account " + event.getDestinationAccountId() + " not found");
            return;
        }

        Account source = sourceOpt.get();
        Account dest = destOpt.get();

        // Step 3: Verify funds availability
        if (source.getBalance().compareTo(event.getAmount()) < 0) {
            String reason = String.format("Insufficient funds in account %s. Available: %s, Required: %s",
                    source.getAccountId(), source.getBalance(), event.getAmount());
            log.warn(reason);
            notifyFailure(event.getPaymentId(), reason);
            return;
        }

        // Step 4: Perform atomic balance adjustment (Protected by @Version optimistic locking)
        source.setBalance(source.getBalance().subtract(event.getAmount()));
        dest.setBalance(dest.getBalance().add(event.getAmount()));

        accountRepository.save(source);
        accountRepository.save(dest);

        // Step 5: Post immutable Double-Entry Journal Entry
        // Invariant: sum(Debits) == sum(Credits)
        JournalEntry journalEntry = JournalEntry.builder()
                .referenceId(event.getPaymentId())
                .description("Payment transfer from " + source.getAccountId() + " to " + dest.getAccountId())
                .totalAmount(event.getAmount())
                .currency(event.getCurrency())
                .build();

        JournalLine debitLine = JournalLine.builder()
                .accountId(source.getAccountId())
                .entryType(EntryType.DEBIT)
                .amount(event.getAmount())
                .build();

        JournalLine creditLine = JournalLine.builder()
                .accountId(dest.getAccountId())
                .entryType(EntryType.CREDIT)
                .amount(event.getAmount())
                .build();

        journalEntry.addLine(debitLine);
        journalEntry.addLine(creditLine);

        JournalEntry savedEntry = journalEntryRepository.save(journalEntry);
        log.info("Successfully posted JournalEntry [{}] with balanced debits and credits for Payment [{}]",
                savedEntry.getId(), event.getPaymentId());

        // Step 6: Emit Saga success event to Kafka
        LedgerProcessedEvent processedEvent = LedgerProcessedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(event.getPaymentId())
                .status("SUCCESS")
                .journalEntryId(savedEntry.getId())
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send(ledgerProcessedTopic, event.getPaymentId().toString(), processedEvent);
    }

    private void notifyFailure(UUID paymentId, String reason) {
        LedgerProcessedEvent failedEvent = LedgerProcessedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(paymentId)
                .status("FAILED")
                .failureReason(reason)
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send(ledgerProcessedTopic, paymentId.toString(), failedEvent);
    }

    public Optional<JournalEntryResponse> getJournalEntryByReference(UUID referenceId) {
        return journalEntryRepository.findByReferenceId(referenceId)
                .map(entry -> JournalEntryResponse.builder()
                        .id(entry.getId())
                        .referenceId(entry.getReferenceId())
                        .description(entry.getDescription())
                        .totalAmount(entry.getTotalAmount())
                        .currency(entry.getCurrency())
                        .createdAt(entry.getCreatedAt())
                        .lines(entry.getLines().stream()
                                .map(line -> JournalEntryResponse.LineDto.builder()
                                        .accountId(line.getAccountId())
                                        .entryType(line.getEntryType())
                                        .amount(line.getAmount())
                                        .build())
                                .collect(Collectors.toList()))
                        .build());
    }
}
