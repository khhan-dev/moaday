package com.couponwith.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity @IdClass(PostTagId.class) @Table(name = "post_tags")
public class PostTag {
    @Id @Column(name = "post_id") private UUID postId;
    @Id private String tag;
    protected PostTag() {} public PostTag(UUID postId, String tag) { this.postId = postId; this.tag = tag; }
    public String getTag() { return tag; }
}
