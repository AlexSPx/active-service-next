package com.services.active.services;

import com.services.active.exceptions.ConflictException;
import com.services.active.exceptions.BadRequestException;
import com.services.active.models.IdempotencyReceiptId;
import com.services.active.repository.IdempotencyReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final IdempotencyReceiptRepository receiptRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public <T> T execute(
            String ownerId,
            String operationId,
            String method,
            String path,
            Object request,
            Class<T> responseType,
            Supplier<T> operation) {
        if (operationId == null) {
            return operation.get();
        }
        if (operationId.isBlank() || operationId.length() > 255) {
            throw new BadRequestException("Idempotency-Key must be between 1 and 255 characters");
        }

        String fingerprint = fingerprint(method, path, request);
        int inserted = receiptRepository.claim(ownerId, operationId, fingerprint, Instant.now());
        var receipt = receiptRepository.findById(new IdempotencyReceiptId(ownerId, operationId))
                .orElseThrow(() -> new IllegalStateException("Idempotency result disappeared"));

        if (inserted == 0) {
            if (!fingerprint.equals(receipt.getFingerprint())) {
                throw new ConflictException("Idempotency key was already used for a different request");
            }
            return deserialize(receipt.getResponseBody(), responseType);
        }

        T response = operation.get();
        receipt.complete(serialize(response));
        return response;
    }

    private String fingerprint(String method, String path, Object request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (method + "\n" + path + "\n" + canonicalRequest(request)).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String canonicalRequest(Object request) {
        if (request == null) {
            return "null";
        }
        JsonNode tree = jsonMapper.valueToTree(request);
        removeNewNullPreconditions(tree);
        return jsonMapper.writeValueAsString(canonicalize(tree));
    }

    private void removeNewNullPreconditions(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            if (object.has("baseVersion") && object.get("baseVersion").isNull()) {
                object.remove("baseVersion");
            }
            if (object.has("profileBaseVersion") && object.get("profileBaseVersion").isNull()) {
                object.remove("profileBaseVersion");
            }
            object.forEachEntry((name, value) -> removeNewNullPreconditions(value));
        } else if (node.isArray()) {
            node.forEach(this::removeNewNullPreconditions);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = jsonMapper.createObjectNode();
            TreeMap<String, JsonNode> properties = new TreeMap<>();
            node.forEachEntry(properties::put);
            properties.forEach((name, value) -> sorted.set(name, canonicalize(value)));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = jsonMapper.createArrayNode();
            node.forEach(value -> array.add(canonicalize(value)));
            return array;
        }
        return node;
    }

    private String serialize(Object value) {
        return value == null ? null : jsonMapper.writeValueAsString(value);
    }

    private <T> T deserialize(String value, Class<T> responseType) {
        return value == null ? null : jsonMapper.readValue(value, responseType);
    }
}
