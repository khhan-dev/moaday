package com.couponwith.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_reminders")
public class EventReminder {
    @Id private UUID id;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(name = "minutes_before", nullable = false) private int minutesBefore;
    @Column(nullable = false) private String channel;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected EventReminder() {}
    public EventReminder(UUID id, UUID eventId, int minutesBefore) {
        this.id = id;
        this.eventId = eventId;
        this.minutesBefore = minutesBefore;
        this.channel = "IN_APP";
        this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public int getMinutesBefore() { return minutesBefore; }
    public String getChannel() { return channel; }
}
