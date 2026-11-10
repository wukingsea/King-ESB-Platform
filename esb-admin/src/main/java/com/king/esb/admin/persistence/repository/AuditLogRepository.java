package com.king.esb.admin.persistence.repository;

import com.king.esb.admin.persistence.entity.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the audit trail. */
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {
}
