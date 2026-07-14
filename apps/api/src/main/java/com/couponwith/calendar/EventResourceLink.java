package com.couponwith.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_resource_links")
public class EventResourceLink {
    @Id private UUID id;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(name = "post_id") private UUID postId;
    @Column(name = "attachment_id") private UUID attachmentId;
    @Column(name = "coupon_id") private UUID couponId;
    @Column(name = "added_by", nullable = false) private UUID addedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected EventResourceLink() {}

    public EventResourceLink(UUID id, UUID eventId, EventResourceType type, UUID resourceId, UUID addedBy) {
        this.id = id;
        this.eventId = eventId;
        this.addedBy = addedBy;
        this.createdAt = Instant.now();
        switch (type) {
            case POST -> this.postId = resourceId;
            case ATTACHMENT -> this.attachmentId = resourceId;
            case COUPON -> this.couponId = resourceId;
        }
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getPostId() { return postId; }
    public UUID getAttachmentId() { return attachmentId; }
    public UUID getCouponId() { return couponId; }
    public UUID getAddedBy() { return addedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public EventResourceType getType() {
        if (postId != null) return EventResourceType.POST;
        if (attachmentId != null) return EventResourceType.ATTACHMENT;
        return EventResourceType.COUPON;
    }
    public UUID getResourceId() {
        return switch (getType()) {
            case POST -> postId;
            case ATTACHMENT -> attachmentId;
            case COUPON -> couponId;
        };
    }
}
