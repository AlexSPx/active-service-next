package com.services.active.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyReceiptId implements Serializable {
    private String ownerId;
    private String operationId;
}
