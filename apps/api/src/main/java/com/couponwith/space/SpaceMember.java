package com.couponwith.space;

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
@IdClass(SpaceMemberId.class)
@Table(name = "space_members")
public class SpaceMember {
    @Id
    @Column(name = "space_id")
    private UUID spaceId;
    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpaceRole role;
    @Column(nullable = false)
    private String status;
    @Column(name = "coupon_redeem_allowed", nullable = false)
    private boolean couponRedeemAllowed;
    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected SpaceMember() {}

    public SpaceMember(UUID spaceId, UUID userId, SpaceRole role) {
        this.spaceId = spaceId;
        this.userId = userId;
        this.role = role;
        this.status = "ACTIVE";
        this.couponRedeemAllowed = role != SpaceRole.VIEWER;
        this.joinedAt = Instant.now();
    }

    public UUID getSpaceId() { return spaceId; }
    public UUID getUserId() { return userId; }
    public SpaceRole getRole() { return role; }
    public String getStatus() { return status; }
    public Instant getJoinedAt() { return joinedAt; }

    public void changeRole(SpaceRole role) {
        this.role = role;
        this.couponRedeemAllowed = role != SpaceRole.VIEWER;
    }

    public void remove() {
        this.status = "REMOVED";
        this.couponRedeemAllowed = false;
    }
}
