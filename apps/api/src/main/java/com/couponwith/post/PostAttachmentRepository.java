package com.couponwith.post;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PostAttachmentRepository extends JpaRepository<PostAttachment, UUID> {
    List<PostAttachment> findByPostIdOrderByCreatedAt(UUID postId);
    long countByPostId(UUID postId);
}
