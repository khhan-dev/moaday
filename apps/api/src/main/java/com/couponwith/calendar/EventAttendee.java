package com.couponwith.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@IdClass(EventAttendeeId.class)
@Table(name = "event_attendees")
public class EventAttendee {
    @Id @Column(name = "event_id") private UUID eventId;
    @Id @Column(name = "user_id") private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AttendanceStatus response;
    @Column(name = "responded_at") private Instant respondedAt;

    protected EventAttendee() {}
    public EventAttendee(UUID eventId, UUID userId, AttendanceStatus response) {
        this.eventId = eventId;
        this.userId = userId;
        respond(response);
    }
    public void respond(AttendanceStatus response) {
        this.response = response;
        this.respondedAt = response == AttendanceStatus.PENDING ? null : Instant.now();
    }
    public UUID getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public AttendanceStatus getResponse() { return response; }
}
