package com.bank.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted by the Ledger Service once double-entry records are posted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerProcessedEvent {
    private UUID eventId;
    private UUID paymentId;
    private String status; // SUCCESS or FAILED
    private Long journalEntryId;
    private String failureReason;
    private Instant timestamp;
}
