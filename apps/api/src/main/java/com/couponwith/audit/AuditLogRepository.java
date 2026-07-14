package com.couponwith.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findTop100BySpaceIdOrderByCreatedAtDesc(UUID spaceId);
    List<AuditLog> findTop100ByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, UUID resourceId);
}
