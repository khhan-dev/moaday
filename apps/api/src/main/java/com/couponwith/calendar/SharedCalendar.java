package com.couponwith.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendars")
public class SharedCalendar {
    @Id private UUID id;
    @Column(name = "space_id", nullable = false) private UUID spaceId;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String color;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected SharedCalendar() {}

    public SharedCalendar(UUID id, UUID spaceId, String name, String color, UUID createdBy) {
        this.id = id;
        this.spaceId = spaceId;
        this.name = name;
        this.color = color;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSpaceId() { return spaceId; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public UUID getCreatedBy() { return createdBy; }
    public void update(String name, String color) { this.name = name; this.color = color; }
}
