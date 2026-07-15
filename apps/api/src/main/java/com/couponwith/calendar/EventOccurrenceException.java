package com.couponwith.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_occurrence_exceptions")
public class EventOccurrenceException {
    @Id private UUID id;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(name = "original_starts_at", nullable = false) private Instant originalStartsAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OccurrenceExceptionAction action;
    private String title;
    @Column(length = 4000) private String description;
    private String location;
    @Column(name = "all_day") private Boolean allDay;
    @Column(name = "starts_at") private Instant startsAt;
    @Column(name = "ends_at") private Instant endsAt;
    private String timezone;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected EventOccurrenceException() {}

    public EventOccurrenceException(UUID id, UUID eventId, Instant originalStartsAt, UUID updatedBy) {
        this.id = id; this.eventId = eventId; this.originalStartsAt = originalStartsAt;
        this.updatedBy = updatedBy; this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }

    public void cancel(UUID actorId) {
        action = OccurrenceExceptionAction.CANCELLED; title = null; description = null; location = null;
        allDay = null; startsAt = null; endsAt = null; timezone = null; touch(actorId);
    }

    public void override(String title, String description, String location, boolean allDay,
                         Instant startsAt, Instant endsAt, String timezone, UUID actorId) {
        action = OccurrenceExceptionAction.OVERRIDE; this.title = title; this.description = description;
        this.location = location; this.allDay = allDay; this.startsAt = startsAt; this.endsAt = endsAt;
        this.timezone = timezone; touch(actorId);
    }

    private void touch(UUID actorId) { this.updatedBy = actorId; this.updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public Instant getOriginalStartsAt() { return originalStartsAt; }
    public OccurrenceExceptionAction getAction() { return action; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public Boolean getAllDay() { return allDay; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getTimezone() { return timezone; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
