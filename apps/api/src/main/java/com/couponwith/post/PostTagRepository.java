package com.couponwith.post;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PostTagRepository extends JpaRepository<PostTag, PostTagId> {
    List<PostTag> findByPostIdOrderByTag(UUID postId);
    void deleteByPostId(UUID postId);
}
