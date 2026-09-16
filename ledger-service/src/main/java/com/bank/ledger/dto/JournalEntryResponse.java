package com.bank.ledger.dto;

import com.bank.ledger.entity.EntryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryResponse {
    private Long id;
    private UUID referenceId;
    private String description;
    private BigDecimal totalAmount;
    private String currency;
    private Instant createdAt;
    private List<LineDto> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineDto {
        private String accountId;
        private EntryType entryType;
        private BigDecimal amount;
    }
}
