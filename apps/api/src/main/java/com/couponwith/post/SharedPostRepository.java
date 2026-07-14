package com.couponwith.post;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SharedPostRepository extends JpaRepository<SharedPost, UUID> {
    List<SharedPost> findBySpaceIdAndStatusOrderByPinnedDescUpdatedAtDesc(UUID spaceId, String status);
}
