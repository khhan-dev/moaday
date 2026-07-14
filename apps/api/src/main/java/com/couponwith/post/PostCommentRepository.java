package com.couponwith.post;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {
    List<PostComment> findByPostIdAndStatusOrderByCreatedAt(UUID postId, String status);
    long countByPostIdAndStatus(UUID postId, String status);
}
