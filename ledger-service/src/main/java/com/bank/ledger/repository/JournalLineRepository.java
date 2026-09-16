package com.bank.ledger.repository;

import com.bank.ledger.entity.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {
    List<JournalLine> findByAccountId(String accountId);
}
