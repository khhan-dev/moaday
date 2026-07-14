package com.couponwith.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
public class CalendarEvent {
    @Id private UUID id;
    @Column(name = "calendar_id", nullable = false) private UUID calendarId;
    @Column(name = "space_id", nullable = false) private UUID spaceId;
    @Column(nullable = false, unique = true) private String uid;
    @Column(nullable = false) private String title;
    @Column(length = 4000) private String description;
    private String location;
    @Column(name = "external_url", length = 1000) private String externalUrl;
    @Column(name = "all_day", nullable = false) private boolean allDay;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Column(nullable = false) private String timezone;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private EventRecurrence recurrence;
    @Column(name = "recurrence_until") private Instant recurrenceUntil;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CalendarEvent() {}

    public CalendarEvent(UUID id, UUID calendarId, UUID spaceId, String title, String description,
                         String location, String externalUrl, boolean allDay, Instant startsAt, Instant endsAt,
                         String timezone, EventRecurrence recurrence, Instant recurrenceUntil, UUID createdBy) {
        this.id = id;
        this.calendarId = calendarId;
        this.spaceId = spaceId;
        this.uid = id + "@moaday.local";
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.version = 0;
        update(calendarId, title, description, location, externalUrl, allDay, startsAt, endsAt, timezone, recurrence, recurrenceUntil);
    }

    public void update(UUID calendarId, String title, String description, String location, String externalUrl,
                       boolean allDay, Instant startsAt, Instant endsAt, String timezone,
                       EventRecurrence recurrence, Instant recurrenceUntil) {
        this.calendarId = calendarId;
        this.title = title;
        this.description = description;
        this.location = location;
        this.externalUrl = externalUrl;
        this.allDay = allDay;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.timezone = timezone;
        this.recurrence = recurrence;
        this.recurrenceUntil = recurrenceUntil;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCalendarId() { return calendarId; }
    public UUID getSpaceId() { return spaceId; }
    public String getUid() { return uid; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public String getExternalUrl() { return externalUrl; }
    public boolean isAllDay() { return allDay; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getTimezone() { return timezone; }
    public EventRecurrence getRecurrence() { return recurrence; }
    public Instant getRecurrenceUntil() { return recurrenceUntil; }
    public UUID getCreatedBy() { return createdBy; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
