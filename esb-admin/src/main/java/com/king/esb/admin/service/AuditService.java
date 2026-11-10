package com.king.esb.admin.service;

import com.king.esb.admin.persistence.entity.AuditLogEntry;
import com.king.esb.admin.persistence.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Best-effort audit trail writer; failures are logged and never break the caller. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String actor, String action, String target, String detail) {
        try {
            AuditLogEntry entry = new AuditLogEntry();
            entry.setActor(actor == null ? "system" : actor);
            entry.setAction(action);
            entry.setTarget(target);
            entry.setDetail(detail);
            entry.setCreatedAt(Instant.now());
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("Failed to write audit entry for {} {}: {}", action, target, e.getMessage());
        }
    }
}
