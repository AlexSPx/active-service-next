package com.services.active.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyReceiptId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyReceipt {
    @Id
    @Column(name = "owner_id", nullable = false, columnDefinition = "TEXT")
    private String ownerId;

    @Id
    @Column(name = "operation_id", nullable = false, columnDefinition = "TEXT")
    private String operationId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String fingerprint;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public void complete(String responseBody) {
        this.responseBody = responseBody;
    }
}
