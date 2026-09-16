package com.bank.ledger.service;

import com.bank.ledger.dto.AccountBalanceResponse;
import com.bank.ledger.entity.Account;
import com.bank.ledger.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    public Optional<AccountBalanceResponse> getAccountBalance(String accountId) {
        return accountRepository.findByAccountId(accountId)
                .map(this::mapToResponse);
    }

    @Transactional
    public void seedInitialAccounts() {
        if (!accountRepository.existsById("ACC-1001")) {
            accountRepository.save(Account.builder()
                    .accountId("ACC-1001")
                    .holderName("Alice Smith")
                    .balance(new BigDecimal("10000.00"))
                    .currency("USD")
                    .status("ACTIVE")
                    .build());
            log.info("Seeded account ACC-1001 with balance $10,000.00");
        }

        if (!accountRepository.existsById("ACC-2002")) {
            accountRepository.save(Account.builder()
                    .accountId("ACC-2002")
                    .holderName("Bob Jones")
                    .balance(new BigDecimal("2500.00"))
                    .currency("USD")
                    .status("ACTIVE")
                    .build());
            log.info("Seeded account ACC-2002 with balance $2,500.00");
        }
    }

    private AccountBalanceResponse mapToResponse(Account account) {
        return AccountBalanceResponse.builder()
                .accountId(account.getAccountId())
                .holderName(account.getHolderName())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .version(account.getVersion())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
