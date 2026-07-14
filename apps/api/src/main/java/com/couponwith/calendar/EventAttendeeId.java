package com.couponwith.calendar;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class EventAttendeeId implements Serializable {
    private UUID eventId;
    private UUID userId;

    public EventAttendeeId() {}
    public EventAttendeeId(UUID eventId, UUID userId) { this.eventId = eventId; this.userId = userId; }
    @Override public boolean equals(Object value) { return value instanceof EventAttendeeId other && Objects.equals(eventId, other.eventId) && Objects.equals(userId, other.userId); }
    @Override public int hashCode() { return Objects.hash(eventId, userId); }
}
