package com.civicworks.platform.idempotency;

import com.civicworks.platform.error.BusinessException;
import com.civicworks.platform.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public <T> ResponseEntity<T> executeIdempotent(String scope, String key, Object requestBody,
                                                     Supplier<T> operation, Class<T> responseType) {
        if (key == null || key.isBlank()) {
            throw BusinessException.badRequest(ErrorCode.MISSING_IDEMPOTENCY_KEY,
                    "Idempotency-Key header is required");
        }

        String requestHash = computeHash(requestBody);
        Optional<IdempotencyKey> existing = repository.findByScopeAndKey(scope, key);

        if (existing.isPresent()) {
            IdempotencyKey ik = existing.get();
            if (!ik.getRequestHash().equals(requestHash)) {
                throw BusinessException.conflict(
                        ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD,
                        "Idempotency key reused with different payload");
            }
            if (ik.getStatus() == IdempotencyKey.Status.COMPLETED) {
                try {
                    T body = objectMapper.readValue(ik.getResponseSnapshot(), responseType);
                    return ResponseEntity.status(ik.getResponseStatus()).body(body);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to deserialize idempotent response", e);
                }
            }
            // PENDING - operation in progress by another thread, return conflict
            throw BusinessException.conflict(ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD,
                    "Operation in progress");
        }

        // Reserve key
        IdempotencyKey ik = new IdempotencyKey();
        ik.setScope(scope);
        ik.setKey(key);
        ik.setRequestHash(requestHash);
        ik.setStatus(IdempotencyKey.Status.PENDING);
        repository.save(ik);

        T result = operation.get();

        // Complete
        try {
            ik.setResponseSnapshot(objectMapper.writeValueAsString(result));
            ik.setResponseStatus(201);
            ik.setStatus(IdempotencyKey.Status.COMPLETED);
            repository.save(ik);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize idempotent response", e);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private String computeHash(Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute request hash", e);
        }
    }
}
