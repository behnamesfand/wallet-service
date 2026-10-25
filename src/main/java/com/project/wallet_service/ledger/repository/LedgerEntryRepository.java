
package com.project.wallet_service.ledger.repository;

import com.project.wallet_service.ledger.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository
        extends JpaRepository<LedgerEntry, Long> {

    Page<LedgerEntry> findByAccount_Id(
            Long accountId,
            Pageable pageable
    );

    List<LedgerEntry> findByTransaction_IdOrderByCreatedAtAsc(
            Long transactionId
    );

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(
            Long accountId);
}
