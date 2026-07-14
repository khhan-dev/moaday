package com.couponwith.space;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class SpaceMemberId implements Serializable {
    private UUID spaceId;
    private UUID userId;

    public SpaceMemberId() {}
    public SpaceMemberId(UUID spaceId, UUID userId) {
        this.spaceId = spaceId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SpaceMemberId that)) return false;
        return Objects.equals(spaceId, that.spaceId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() { return Objects.hash(spaceId, userId); }
}
