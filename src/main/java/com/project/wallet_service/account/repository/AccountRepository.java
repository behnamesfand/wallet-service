package com.project.wallet_service.account.repository;

import com.project.wallet_service.account.entity.Account;
import com.project.wallet_service.account.enums.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository
        extends JpaRepository<Account, Long> {

    List<Account> findByOwnerId(String ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(
            @Param("id") Long id);

    Optional<Account> findByAccountTypeAndCurrency(
            AccountType accountType,
            String currency
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
       select a from Account a
       where a.accountType = :accountType
         and a.currency = :currency
       """)
    Optional<Account> findByAccountTypeAndCurrencyForUpdate(
            @Param("accountType") AccountType accountType,
            @Param("currency") String currency
    );

    Optional<Account> findByIdAndOwnerId(
            Long id,
            String ownerId
    );
}
