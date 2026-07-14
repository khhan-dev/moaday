package com.couponwith.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_reminder_deliveries")
public class EventReminderDelivery {
    @Id private UUID id;
    @Column(name = "reminder_id", nullable = false) private UUID reminderId;
    @Column(name = "occurrence_starts_at", nullable = false) private Instant occurrenceStartsAt;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "delivered_at", nullable = false) private Instant deliveredAt;

    protected EventReminderDelivery() {}

    public EventReminderDelivery(UUID id, UUID reminderId, Instant occurrenceStartsAt, UUID userId, Instant deliveredAt) {
        this.id = id;
        this.reminderId = reminderId;
        this.occurrenceStartsAt = occurrenceStartsAt;
        this.userId = userId;
        this.deliveredAt = deliveredAt;
    }
}
