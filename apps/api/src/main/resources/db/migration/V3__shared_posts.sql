CREATE TABLE posts (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES spaces(id),
    author_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(160) NOT NULL,
    content VARCHAR(10000) NOT NULL,
    pinned BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE post_tags (
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    tag VARCHAR(40) NOT NULL,
    PRIMARY KEY (post_id, tag)
);

CREATE TABLE post_attachments (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(100) NOT NULL UNIQUE,
    uploaded_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE post_comments (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id),
    content VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_posts_space_updated ON posts(space_id, updated_at);
CREATE INDEX idx_post_tags_tag ON post_tags(tag);
CREATE INDEX idx_post_comments_post_created ON post_comments(post_id, created_at);
CREATE INDEX idx_post_attachments_post ON post_attachments(post_id);
