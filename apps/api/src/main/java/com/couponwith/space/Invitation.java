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
@Table(name = "invitations")
public class Invitation {
    @Id
    private UUID id;
    @Column(name = "space_id", nullable = false)
    private UUID spaceId;
    @Column(nullable = false)
    private String email;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpaceRole role;
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "accepted_at")
    private Instant acceptedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Invitation() {}

    public Invitation(UUID id, UUID spaceId, String email, SpaceRole role, String tokenHash, UUID invitedBy, Instant expiresAt) {
        this.id = id;
        this.spaceId = spaceId;
        this.email = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSpaceId() { return spaceId; }
    public String getEmail() { return email; }
    public SpaceRole getRole() { return role; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isActive() { return acceptedAt == null && revokedAt == null && expiresAt.isAfter(Instant.now()); }
    public void accept() { this.acceptedAt = Instant.now(); }
    public void revoke() { this.revokedAt = Instant.now(); }

    public String status() {
        if (acceptedAt != null) return "ACCEPTED";
        if (revokedAt != null) return "REVOKED";
        if (!expiresAt.isAfter(Instant.now())) return "EXPIRED";
        return "PENDING";
    }
}
