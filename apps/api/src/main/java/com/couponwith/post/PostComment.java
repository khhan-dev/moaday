package com.couponwith.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "post_comments")
public class PostComment {
    @Id private UUID id;
    @Column(name = "post_id", nullable = false) private UUID postId;
    @Column(name = "author_id", nullable = false) private UUID authorId;
    @Column(nullable = false, length = 2000) private String content;
    @Column(nullable = false) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected PostComment() {}
    public PostComment(UUID id, UUID postId, UUID authorId, String content) { this.id=id; this.postId=postId; this.authorId=authorId; this.status="ACTIVE"; this.createdAt=Instant.now(); update(content); }
    public void update(String content) { this.content=content; this.updatedAt=Instant.now(); } public void delete() { this.status="DELETED"; this.updatedAt=Instant.now(); }
    public UUID getId(){return id;} public UUID getPostId(){return postId;} public UUID getAuthorId(){return authorId;} public String getContent(){return content;}
    public String getStatus(){return status;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
