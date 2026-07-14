package com.couponwith.space;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceMemberRepository extends JpaRepository<SpaceMember, SpaceMemberId> {
    List<SpaceMember> findByUserIdAndStatusOrderByJoinedAt(UUID userId, String status);
    List<SpaceMember> findBySpaceIdAndStatusOrderByJoinedAt(UUID spaceId, String status);
    Optional<SpaceMember> findBySpaceIdAndUserIdAndStatus(UUID spaceId, UUID userId, String status);
}
