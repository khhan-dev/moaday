package com.couponwith.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id private UUID id;
    @Column(name = "space_id", nullable = false) private UUID spaceId;
    @Column(name = "actor_id") private UUID actorId;
    @Column(nullable = false) private String action;
    @Column(name = "resource_type", nullable = false) private String resourceType;
    @Column(name = "resource_id") private UUID resourceId;
    @Column(nullable = false, length = 500) private String summary;
    @Column(length = 500) private String reason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected AuditLog() {}
    public AuditLog(UUID id, UUID spaceId, UUID actorId, String action, String resourceType,
                    UUID resourceId, String summary, String reason, Instant createdAt) {
        this.id=id;this.spaceId=spaceId;this.actorId=actorId;this.action=action;this.resourceType=resourceType;
        this.resourceId=resourceId;this.summary=summary;this.reason=reason;this.createdAt=createdAt;
    }
    public UUID getId(){return id;} public UUID getSpaceId(){return spaceId;} public UUID getActorId(){return actorId;}
    public String getAction(){return action;} public String getResourceType(){return resourceType;} public UUID getResourceId(){return resourceId;}
    public String getSummary(){return summary;} public String getReason(){return reason;} public Instant getCreatedAt(){return createdAt;}
}
