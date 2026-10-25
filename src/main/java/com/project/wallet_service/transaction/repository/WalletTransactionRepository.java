
package com.project.wallet_service.transaction.repository;

import com.project.wallet_service.transaction.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletTransactionRepository
        extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByIdempotencyKey(
            String idempotencyKey
    );

    @EntityGraph(attributePaths = {
            "sourceAccount",
            "targetAccount"
    })
    Optional<WalletTransaction> findWithAccountsByIdempotencyKey(
            String idempotencyKey
    );

    @EntityGraph(attributePaths = {
            "sourceAccount",
            "targetAccount"
    })
    @Query("""
            SELECT tx
            FROM WalletTransaction tx
            LEFT JOIN tx.sourceAccount source
            LEFT JOIN tx.targetAccount target
            WHERE tx.id = :transactionId
              AND (
                  source.ownerId = :ownerId
                  OR target.ownerId = :ownerId
              )
            """)
    Optional<WalletTransaction> findAccessibleTransaction(
            @Param("transactionId") Long transactionId,
            @Param("ownerId") String ownerId
    );

    @EntityGraph(attributePaths = {
            "sourceAccount",
            "targetAccount"
    })
    @Query(
            value = """
                    SELECT tx
                    FROM WalletTransaction tx
                    LEFT JOIN tx.sourceAccount source
                    LEFT JOIN tx.targetAccount target
                    WHERE source.ownerId = :ownerId
                       OR target.ownerId = :ownerId
                    """,
            countQuery = """
                    SELECT COUNT(tx)
                    FROM WalletTransaction tx
                    LEFT JOIN tx.sourceAccount source
                    LEFT JOIN tx.targetAccount target
                    WHERE source.ownerId = :ownerId
                       OR target.ownerId = :ownerId
                    """
    )
    Page<WalletTransaction> findTransactionsForOwner(
            @Param("ownerId") String ownerId,
            Pageable pageable
    );
}
