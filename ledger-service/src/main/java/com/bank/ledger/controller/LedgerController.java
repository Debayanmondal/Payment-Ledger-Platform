package com.bank.ledger.controller;

import com.bank.ledger.dto.AccountBalanceResponse;
import com.bank.ledger.dto.JournalEntryResponse;
import com.bank.ledger.service.AccountService;
import com.bank.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final AccountService accountService;
    private final LedgerService ledgerService;

    /**
     * Seeds initial demo accounts for testing transfers.
     */
    @PostMapping("/seed")
    public ResponseEntity<Map<String, String>> seedAccounts() {
        accountService.seedInitialAccounts();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Initialized test accounts ACC-1001 ($10,000.00) and ACC-2002 ($2,500.00)"
        ));
    }

    /**
     * Inspects current balance and version for an account.
     */
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<?> getAccountBalance(@PathVariable String accountId) {
        return accountService.getAccountBalance(accountId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Inspects immutable double-entry journal entry and lines by payment reference ID.
     */
    @GetMapping("/entries/{referenceId}")
    public ResponseEntity<?> getJournalEntry(@PathVariable UUID referenceId) {
        return ledgerService.getJournalEntryByReference(referenceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
