package com.couponwith.post;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class PostTagId implements Serializable {
    private UUID postId; private String tag;
    public PostTagId() {} public PostTagId(UUID postId, String tag) { this.postId = postId; this.tag = tag; }
    @Override public boolean equals(Object value) { return value instanceof PostTagId other && Objects.equals(postId, other.postId) && Objects.equals(tag, other.tag); }
    @Override public int hashCode() { return Objects.hash(postId, tag); }
}
