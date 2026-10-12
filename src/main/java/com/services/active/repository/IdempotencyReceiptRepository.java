package com.services.active.repository;

import com.services.active.models.IdempotencyReceipt;
import com.services.active.models.IdempotencyReceiptId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface IdempotencyReceiptRepository extends JpaRepository<IdempotencyReceipt, IdempotencyReceiptId> {
    @Modifying
    @Query("""
            INSERT INTO IdempotencyReceipt (ownerId, operationId, fingerprint, createdAt)
            VALUES (:ownerId, :operationId, :fingerprint, :createdAt)
            ON CONFLICT (ownerId, operationId) DO NOTHING
            """)
    int claim(
            @Param("ownerId") String ownerId,
            @Param("operationId") String operationId,
            @Param("fingerprint") String fingerprint,
            @Param("createdAt") Instant createdAt);

    @Modifying
    @Query("DELETE FROM IdempotencyReceipt WHERE ownerId = :ownerId")
    void deleteByOwnerId(@Param("ownerId") String ownerId);
}
