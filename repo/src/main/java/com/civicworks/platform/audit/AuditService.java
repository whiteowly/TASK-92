package com.civicworks.platform.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog log(Long actorId, String actorRole, String action,
                        String entityType, String entityId, String afterSnapshot) {
        return log(actorId, actorRole, action, entityType, entityId, null, afterSnapshot);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog log(Long actorId, String actorRole, String action,
                        String entityType, String entityId,
                        String beforeSnapshot, String afterSnapshot) {
        AuditLog entry = new AuditLog();
        entry.setActorId(actorId);
        entry.setActorRole(actorRole);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setBeforeSnapshot(beforeSnapshot);
        entry.setAfterSnapshot(afterSnapshot);
        entry.setRequestId(MDC.get("requestId"));
        entry.setOutcome("SUCCESS");

        AuditLog saved = repository.save(entry);
        log.info("audit action={} entity={}:{} actor={}", action, entityType, entityId, actorId);
        return saved;
    }
}
