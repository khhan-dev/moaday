package com.couponwith.space;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "spaces")
public class Space {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpaceType type;
    @Column(nullable = false)
    private String name;
    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;
    @Column(nullable = false)
    private String timezone;
    @Column(nullable = false)
    private String color;
    @Column(nullable = false)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Space() {}

    public Space(UUID id, SpaceType type, String name, UUID ownerUserId, String timezone, String color) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.ownerUserId = ownerUserId;
        this.timezone = timezone;
        this.color = color;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public SpaceType getType() { return type; }
    public String getName() { return name; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public String getTimezone() { return timezone; }
    public String getColor() { return color; }
    public void archive() { this.status = "ARCHIVED"; }
}
