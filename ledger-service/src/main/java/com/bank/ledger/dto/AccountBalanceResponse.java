package com.bank.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceResponse {
    private String accountId;
    private String holderName;
    private BigDecimal balance;
    private String currency;
    private String status;
    private Long version;
    private Instant updatedAt;
}
